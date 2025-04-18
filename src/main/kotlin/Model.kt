import javafx.beans.Observable
import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableIntegerValue
import javafx.beans.value.ObservableValue
import java.io.File
import javafx.scene.input.KeyCode
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class Model {
    var videoPath = SimpleStringProperty("")
    var csvPath = SimpleStringProperty("")
    var videoLength = SimpleIntegerProperty(0)
    var loopVideo = SimpleBooleanProperty(true)
    var pauseVideo = SimpleBooleanProperty(false)
    var currentIndex = SimpleIntegerProperty(-1)
    var currentIntensity = SimpleStringProperty("")
    var currentPosition = SimpleStringProperty("")
    var currentNotes = SimpleStringProperty("")
    var dirty = SimpleBooleanProperty(false)
    var timeWindow = SimpleIntegerProperty(1)

    class Entry {
        var intensity = ""
        var position = ""
        var notes = ""

        override fun toString(): String {
            return "$intensity $position $notes"
        }
    }

    var history = ArrayList<Entry>()

    var intensityAliasName = SimpleStringProperty("Intensity")
    var positionAliasName = SimpleStringProperty("Position")

    var intensities = arrayListOf(
        Intensity("Out of frame", "0", arrayListOf(KeyCode.DIGIT0, KeyCode.NUMPAD0)),
        Intensity("Stationary (no limb mvmt)", "1", arrayListOf(KeyCode.DIGIT1, KeyCode.NUMPAD1)),
        Intensity("Limb mvmt/minor trunk (stationary)", "2", arrayListOf(KeyCode.DIGIT2, KeyCode.NUMPAD2)),
        Intensity("Full trunk mvmt", "3", arrayListOf(KeyCode.DIGIT3, KeyCode.NUMPAD3)),
        Intensity("Slow/usual whole body translocation", "4", arrayListOf(KeyCode.DIGIT4, KeyCode.NUMPAD4)),
        Intensity("Fast whole body translocation", "5", arrayListOf(KeyCode.DIGIT5, KeyCode.NUMPAD5)),
        Intensity("Non-Volitional movement", "6", arrayListOf(KeyCode.DIGIT6, KeyCode.NUMPAD6)),
        Intensity("Being pushed (stroller/wagon)", "7", arrayListOf(KeyCode.DIGIT7, KeyCode.NUMPAD7)),
        Intensity("Pulling/touching accelerometer", "8", arrayListOf(KeyCode.DIGIT8, KeyCode.NUMPAD8)),
        Intensity("Not wearing accelerometer", "9", arrayListOf(KeyCode.DIGIT9, KeyCode.NUMPAD9)),
        Intensity("Fall", "X", arrayListOf(KeyCode.X)),
    )

    var positions = arrayListOf(
        Position("Sit/squat/crawl", "A", KeyCode.A),
        Position("Standing", "B", KeyCode.B),
        Position("Lying (trunk on ground)", "C", KeyCode.C),
        Position("Other", "O", KeyCode.O),
    )

    init {
        // Reset history whenever a new video is loaded
        videoLength.addListener { _, _, newLength ->
            // Reset history
            var newHistory = ArrayList<Entry>()
            for (i in 0..(newLength.toInt()/timeWindow.get().toInt())) {
                newHistory.add(Entry())
            }
            history = newHistory
        }

        // Store the modifications in the history whenever a field is modified
        currentIntensity.addListener { _, _, new -> history[currentIndex.get()].intensity = new; dirty.set(true) }
        currentPosition.addListener { _, _, new -> history[currentIndex.get()].position = new; dirty.set(true) }
        currentNotes.addListener { _, _, new -> history[currentIndex.get()].notes = new; dirty.set(true) }

        // Restore the values from the history when index is changed
        currentIndex.addListener { _, _, newIndex ->
            if (newIndex.toInt() < history.size && newIndex.toInt() >= 0) {
                val entry = history[newIndex.toInt()]
                currentIntensity.set(entry.intensity)
                currentPosition.set(entry.position)
                currentNotes.set(entry.notes)
            }
        }
    }

    public fun save(file : File) {
        file.printWriter().use { out ->
            out.println("Index,Timestamp,${intensityAliasName.get()},${positionAliasName.get()},Notes")

            history.forEachIndexed { index, triple ->
                out.println("$index,${index*timeWindow.get().toDouble()},${triple.intensity},${triple.position},\"${triple.notes}\"")
            }
        }
        dirty.set(false)
    }

    public fun load(file : File) {
        var idx = 0
        var newHistory = ArrayList<Entry>()
        file.forEachLine { line ->
            val fields = line.split(",")
            if (idx > 0) {
                val index = fields[0].toInt()
                if (newHistory.size != index) {
                    throw Exception("File is skipping indices. Was expecting Index:${newHistory.size} but got Index:$index")
                }
                var entry = Entry()
                entry.intensity = fields[2]
                entry.position = fields[3]

                // If the note contains commas, we might have splitted on them and created multiple fields, so we have to group them
                entry.notes =  fields[4]
                for (i in 5 until fields.size) {
                    entry.notes += ","+fields[i]
                }
                // Remove the quotes protecting the notes if there is some
                if (entry.notes.startsWith("\"") && entry.notes.endsWith("\"") && entry.notes.length >= 2) {
                    entry.notes = entry.notes.substring(1, entry.notes.length-1)
                    System.out.println(entry.notes)
                }
                newHistory.add(entry)
            }
            idx++
        }
        history = newHistory
        // Force reload
        currentIndex.set(1)
        currentIndex.set(0)
    }

    public fun loadConfig(file : File) {
        // Clear the current config
        intensities.clear()
        positions.clear()

        // Load the new config
        val factory = DocumentBuilderFactory.newInstance()
        val builder: DocumentBuilder = factory.newDocumentBuilder()
        val doc: Document = builder.parse(file)
        doc.documentElement.normalize()
        val config = doc.getElementsByTagName("config").item(0) as Element
        val intensity = config.getElementsByTagName("choice").item(0) as Element
        val intensityEntries = intensity.getElementsByTagName("entry")
        for (i in 0 until intensityEntries.length) {
            val entry = intensityEntries.item(i) as Element
            val name = entry.textContent
            val value = entry.getAttribute("value")
            val shortcut = entry.getAttribute("shortcut")
            val shortcut2 = entry.getAttribute("shortcut2")
            var keys = arrayListOf<KeyCode>()
            if (shortcut != "") {
                keys.add(KeyCode.valueOf(shortcut))
            }
            if (shortcut2 != "") {
                keys.add(KeyCode.valueOf(shortcut2))
            }
            intensities.add(Intensity(name, value, keys))
        }
        val position = config.getElementsByTagName("multichoice").item(0) as Element
        val positionEntries = position.getElementsByTagName("entry")
        for (i in 0 until positionEntries.length) {
            val entry = positionEntries.item(i) as Element
            val name = entry.textContent
            val value = entry.getAttribute("value")
            val shortcut = entry.getAttribute("shortcut")
            positions.add(Position(name, value, KeyCode.valueOf(shortcut)))
        }

        val timeWindow = config.getElementsByTagName("timeWindow").item(0).textContent
        this.timeWindow.set(timeWindow.toInt())
        val intensityAliasName = config.getElementsByTagName("choice").item(0).attributes.getNamedItem("name").textContent
        this.intensityAliasName.set(intensityAliasName)
        val positionAliasName = config.getElementsByTagName("multichoice").item(0).attributes.getNamedItem("name").textContent
        this.positionAliasName.set(positionAliasName)
    }
}