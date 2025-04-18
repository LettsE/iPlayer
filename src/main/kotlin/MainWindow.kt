import javafx.application.Application
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.layout.*
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.text.Font
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.util.Duration
import java.io.File
import javafx.animation.Animation
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.animation.KeyFrame
import javafx.scene.paint.Color
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import javafx.scene.text.Text


class MainWindow : Application() {
    val model = Model()
    val timeLabel = Label("0 ms")
    var mediaView : MediaView? = null
    val muteCheckbox = CheckBox("Mute")
    var lastTime : Double = 0.0
    val rateSpinner = Spinner<Number>(0.1, 8.0, 1.0, 0.1)

    // Related to BUG-Fix (Freezes)
    var detectFreezes = false
    var recoveringFromFreeze = false
    var isPaused = false

    // Load config file if it exists
    init {
        val configFile = File("config.xml")
        if (configFile.exists()) {
            try {
                model.loadConfig(configFile)
            } catch (e: Exception) {
                val alert = Alert(AlertType.ERROR, "Error loading config file: ${e.message}", ButtonType.OK)
                alert.showAndWait()
            }
        }
    }

    override fun start(stage: Stage) {
        mediaView = MediaView()

        // Load a new video
        model.videoPath.addListener { _, _, newPath ->
            if (mediaView?.mediaPlayer != null) {
                mediaView?.mediaPlayer?.dispose()
            }

            alertCall {
                val media = Media(File(newPath).toURI().toString())
                val mediaPlayer = MediaPlayer(media)
                mediaView?.mediaPlayer = mediaPlayer
            }

            mediaView?.mediaPlayer?.setOnStopped {
                println("Video stopped")
            }

            mediaView?.mediaPlayer?.currentTimeProperty()?.addListener{ _, _, newValue ->
                // If is not looping, we should "auto-advance" the currentIndex
                if (!model.loopVideo.get()) {
                    model.currentIndex.set(newValue.toSeconds().toInt())
                }
                timeLabel.text = "${Math.floor(newValue.toMillis())/1000.0} sec / ${Math.floor(model.videoLength.get().toDouble())} sec";

                // Start detecting freezes only after the video started
                detectFreezes = true
            }
            mediaView?.mediaPlayer?.cycleCount = MediaPlayer.INDEFINITE
            mediaView?.mediaPlayer?.muteProperty()?.set(muteCheckbox.isSelected)
            mediaView?.mediaPlayer?.rateProperty()?.set(rateSpinner.valueProperty().get().toDouble())


            model.currentIndex.set(0)
            mediaView?.mediaPlayer?.setOnReady {
                val length = mediaView?.mediaPlayer?.media?.duration?.toSeconds()?.toInt()
                if (length != null) {
                    model.videoLength.set(length)
                }
            }

            mediaView?.mediaPlayer?.statusProperty()?.addListener { _, _, newValue ->
                // Related to BUG-Fix (Freeze)
                if (recoveringFromFreeze) {
                    if (newValue == MediaPlayer.Status.STOPPED)  {
                        mediaView?.mediaPlayer?.play()
                        println("Play...")
                    }

                    if (newValue == MediaPlayer.Status.PLAYING)  {
                        println("Recovered!")
                        recoveringFromFreeze = false
                    }
                }
            }

        }

        // Change looping part of the video
        model.currentIndex.addListener { _, _, newValue ->
            if (model.loopVideo.get()) {
                val startTime = Duration.seconds(newValue.toDouble() * model.timeWindow.get().toDouble())
                val stopTime = Duration.seconds(newValue.toDouble() * model.timeWindow.get().toDouble() + model.timeWindow.get().toDouble())
                mediaView?.mediaPlayer?.startTime = startTime
                mediaView?.mediaPlayer?.stopTime = stopTime
                mediaView?.mediaPlayer?.seek(mediaView?.mediaPlayer?.startTime)
                if (!model.pauseVideo.get()) {
                    mediaView?.mediaPlayer?.play()
                }
            }
        }

        // Change looping property
        model.loopVideo.addListener { _, _, newValue ->
            if (newValue) {
                val startTime = Duration.seconds(model.currentIndex.get().toDouble() * model.timeWindow.get().toDouble())
                val stopTime = Duration.seconds(model.currentIndex.get().toDouble() * model.timeWindow.get().toDouble() + model.timeWindow.get().toDouble())

                mediaView?.mediaPlayer?.startTime = startTime
                mediaView?.mediaPlayer?.stopTime = stopTime
                mediaView?.mediaPlayer?.seek(mediaView?.mediaPlayer?.startTime)
                if (!model.pauseVideo.get()) {
                    mediaView?.mediaPlayer?.play()
                }
            } else {
                mediaView?.mediaPlayer?.startTime = Duration.ZERO
                mediaView?.mediaPlayer?.stopTime = mediaView?.mediaPlayer?.totalDuration
            }
        }

        // Pause/play
        model.pauseVideo.addListener{ _, _, newValue ->
            if (newValue) {
                mediaView?.mediaPlayer?.pause()
                isPaused = true
            } else {
                mediaView?.mediaPlayer?.play()
                isPaused = false
            }
        }

        mediaView?.fitWidth = 640.0
        mediaView?.fitHeight = 480.0

        val vbox = VBox(0.0)
        val top = VBox(5.0)
        val bottom = VBox(8.0)

        top.children.addAll(getMenuBar(), getTitle())
        top.alignment = Pos.TOP_CENTER

        bottom.children.addAll(getVideoInfoPane(), getControlPane(), getSelectionPane())
        bottom.alignment = Pos.TOP_CENTER


        vbox.alignment = Pos.TOP_CENTER
        val videoStackPane = StackPane()
        val annotationLabel = Text("")
        annotationLabel.font = Font.font("Helvetica", FontWeight.EXTRA_BOLD, FontPosture.REGULAR, 35.0)
        annotationLabel.stroke = Color.BLACK
        annotationLabel.strokeWidth = 0.5
        annotationLabel.fill = Color.YELLOW
        StackPane.setAlignment(annotationLabel, Pos.TOP_LEFT)
        videoStackPane.children.addAll(mediaView, annotationLabel)
        vbox.children.addAll(top, videoStackPane, bottom)

        model.currentPosition.addListener { _, _, pos ->
            annotationLabel.text = " " + model.currentIntensity.get() + pos
        }
        model.currentIntensity.addListener { _, _, intensity ->
            annotationLabel.text = " " + intensity + model.currentPosition.get()
        }

        //model.videoPath.set("path")

        val scene = Scene(vbox, 800.0, 600.0)
        scene.onKeyPressed = EventHandler<KeyEvent> {
            // Shortcuts to control Intensity
            for (intensity in model.intensities) {
                if (intensity.keycodes.contains(it.code)) {
                    model.currentIntensity.set(intensity.letter)
                }
            }

            // Shortcuts to control Position
            for (position in model.positions) {
                if (position.keycode == it.code) {
                    // Cannot have twice the same letter
                    if (model.currentPosition.get().isEmpty() || model.currentPosition.get().last().toString() != position.letter) {
                        model.currentPosition.set(model.currentPosition.get() + position.letter)
                    }
                }
            }
            // Backspace removes last position
            if (it.code == KeyCode.BACK_SPACE) {
                if (!model.currentPosition.get().isEmpty()) {
                    val pos = model.currentPosition.get()
                    model.currentPosition.set(pos.substring(0, pos.length-1))
                }
            }

            // Next/Previous
            if (model.currentIndex.get() < model.videoLength.get() &&
                (it.code == KeyCode.ENTER || it.code == KeyCode.RIGHT)) {
                model.currentIndex.set(model.currentIndex.get()+1)
            }
            if (model.currentIndex.get() > 0 && it.code == KeyCode.LEFT) {
                model.currentIndex.set(model.currentIndex.get()-1)
            }
        }
        stage.title = "iPlayer"
        stage.scene = scene

        scene.heightProperty().addListener { _, _, _ ->
            val heightTaken = top.height + bottom.height
            val heightAvailable = vbox.height - heightTaken
            mediaView?.fitHeight = heightAvailable
            mediaView?.fitWidth = vbox.width
        }

        scene.widthProperty().addListener { _, _, _ ->
            val heightTaken = top.height + bottom.height
            val heightAvailable = vbox.height - heightTaken
            mediaView?.fitHeight = heightAvailable
            mediaView?.fitWidth = vbox.width
        }

        stage.heightProperty().addListener { _, _, _ ->
            val heightTaken = top.height + bottom.height
            val heightAvailable = vbox.height - heightTaken
            mediaView?.fitHeight = heightAvailable
            mediaView?.fitWidth = vbox.width
        }

        stage.widthProperty().addListener { _, _, _ ->
            val heightTaken = top.height + bottom.height
            val heightAvailable = vbox.height - heightTaken
            mediaView?.fitHeight = heightAvailable
            mediaView?.fitWidth = vbox.width
        }


        /**
         * BUG fix:
         * Sometimes, the video playback freezes on Windows. It is a bug related with the VideoPlayer.
         * Pausing then playing the video fixes the issue.
         * The following code is a way to do it automatically: First. we have a periodic timer to check if the video is frozen
         * When it is, we simply pause/play the video.
         */
        var timeline: javafx.animation.Timeline = javafx.animation.Timeline(KeyFrame(Duration.millis(450.0), {
            val currentTime = mediaView?.mediaPlayer?.currentTime?.toMillis();
            if (!isPaused && !recoveringFromFreeze && detectFreezes && currentTime == lastTime) {
                println("Freeze detected! Recovering...");
                recoveringFromFreeze = true
                mediaView?.mediaPlayer?.stop()
            }
            if (currentTime != null) {
                lastTime = currentTime
            };
        }))

        timeline.setCycleCount(Animation.INDEFINITE)
        timeline.play()

        stage.show()
    }

    private fun alertCall(callback : ()->Unit) {
        try {
            callback()
        } catch (e : Exception) {
            val alert = Alert(AlertType.ERROR, e.message, ButtonType.OK)
            alert.showAndWait()
        }
    }

    private fun getMenuBar() : MenuBar {
        var menuBar = MenuBar()
        val fileMenu = Menu("File")
        val loadVideoItem = MenuItem("Load video...")
        val loadDataItem = MenuItem("Load CSV...")
        val saveItem = MenuItem("Save")
        saveItem.accelerator = KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)
        val saveAsItem = MenuItem("Save as...")
        fileMenu.items.addAll(loadVideoItem, loadDataItem, SeparatorMenuItem(), saveItem, saveAsItem)

        /* == Load video */
        loadVideoItem.setOnAction {
            val fileChooser = FileChooser()
            fileChooser.title = "Load Video File"
            fileChooser.extensionFilters.addAll(
                FileChooser.ExtensionFilter(
                    "Video Files", "*.mp4", "*.mov", "*.wmv", "*.avi", "*.mkv", "*.flv", "*.swf", "*.mpg", "*.mpeg", "*.ogg"
                ),
                FileChooser.ExtensionFilter(
                    "All Files", "*.*"
                )
            )
            val selectedFile = fileChooser.showOpenDialog(null)
            if (selectedFile != null) {
                alertCall { model.videoPath.set(selectedFile.absolutePath.toString()) }
            }
        }

        /* == Load CSV */
        loadDataItem.setOnAction {
            val fileChooser = FileChooser()
            fileChooser.title = "Load CSV File"
            fileChooser.extensionFilters.addAll(
                FileChooser.ExtensionFilter(
                    "CSV Files", "*.csv"
                ),
                FileChooser.ExtensionFilter(
                    "All Files", "*.*"
                )
            )
            val selectedFile = fileChooser.showOpenDialog(null)

            if (selectedFile != null) {
                alertCall {
                    model.load(selectedFile)
                    model.csvPath.set(selectedFile.absolutePath.toString())
                }
            }
        }

        /* == Save */
        saveItem.setOnAction {
            if (model.csvPath.isEmpty.get()) {
                saveAsItem.onAction.handle(null)
            } else {
                alertCall { model.save(File(model.csvPath.get())) }
            }
        }

        /* == Save As */
        saveAsItem.setOnAction {
            val fileChooser = FileChooser()
            fileChooser.title = "Save CSV File"
            fileChooser.extensionFilters.addAll(
                FileChooser.ExtensionFilter(
                    "CSV Files", "*.csv"
                )
            )
            val selectedFile = fileChooser.showSaveDialog(null)

            if (selectedFile != null) {
                alertCall {
                    model.save(selectedFile)
                    model.csvPath.set(selectedFile.absolutePath.toString())
                }
            }
        }

        menuBar.menus.add(fileMenu)
        return menuBar
    }

    private fun getTitle() : Node {
        var label = Label("")
        label.font = Font("Helvetica", 16.0)

        val updateLabel =  {
            val video = if (model.videoPath.isEmpty.get()) "No video" else File(model.videoPath.get()).name
            var csv = if (model.csvPath.isEmpty.get()) "Untitled" else File(model.csvPath.get()).name

            if (model.dirty.get()) {
                csv += "*"
            }
            label.text = "$video / $csv"
        }

        model.videoPath.addListener { _, _, _ -> updateLabel() }
        model.csvPath.addListener { _, _, _ -> updateLabel() }
        model.dirty.addListener { _, _, _ -> updateLabel() }

        return label
    }

    private fun getHFiller() : Pane {
        val filler = Pane()
        filler.prefHeight = 1.0
        HBox.setHgrow(filler, Priority.ALWAYS)

        return filler
    }

    private fun getVideoInfoPane() : Pane {
        val hbox = HBox(8.0)


        val rateLabel = Label("Playback speed:")

        rateSpinner.prefWidth = 70.0
        rateSpinner.valueProperty().addListener { _, _, newRate ->
            mediaView?.mediaPlayer?.rateProperty()?.set(newRate.toDouble())
        }

        muteCheckbox.selectedProperty().set(true)
        muteCheckbox.setOnAction {
            mediaView?.mediaPlayer?.muteProperty()?.set(muteCheckbox.isSelected)
        }

        val loopCheckbox = CheckBox("Loop")
        loopCheckbox.selectedProperty().set(model.loopVideo.get())
        loopCheckbox.setOnAction {
            model.loopVideo.set(loopCheckbox.isSelected)
        }


        val pauseBtn = Button(if (model.pauseVideo.get()) "Play" else "Pause")
        pauseBtn.setOnAction {
            model.pauseVideo.set(!model.pauseVideo.get())
            pauseBtn.text = if (model.pauseVideo.get()) "Play" else "Pause"
            System.gc()
        }

        val refreshBtn = Button("Refresh")
        refreshBtn.setOnAction {
            mediaView?.mediaPlayer?.stop()
            System.gc()
            mediaView?.mediaPlayer?.play()
        }

        hbox.alignment = Pos.CENTER
        HBox.setMargin(timeLabel, Insets(0.0, 5.0, 0.0, 0.0))
        HBox.setMargin(rateLabel, Insets(0.0, 0.0, 0.0, 5.0))
        HBox.setMargin(muteCheckbox, Insets(0.0, 0.0, 0.0, 10.0))
        hbox.children.addAll(rateLabel, rateSpinner, muteCheckbox, loopCheckbox, pauseBtn, refreshBtn, getHFiller(), timeLabel)

        return hbox
    }

    private fun getControlPane() : Pane {
        val hbox = HBox(0.0)
        val previousBtn = Button("<")
        previousBtn.minWidth = 25.0
        previousBtn.minHeight = 50.0
        HBox.setMargin(previousBtn, Insets(0.0, 0.0, 0.0, 5.0))
        previousBtn.setOnAction {
            model.currentIndex.set(model.currentIndex.get()-1)
        }

        val nextBtn = Button(">")
        nextBtn.minWidth = 25.0
        nextBtn.minHeight = 50.0
        HBox.setMargin(nextBtn, Insets(0.0, 5.0, 0.0, 0.0))
        nextBtn.setOnAction {
            model.currentIndex.set(model.currentIndex.get()+1)
        }

        if (model.videoPath.get().isEmpty()) {
            previousBtn.disableProperty().set(true)
            nextBtn.disableProperty().set(true)
        }

        // Disable prev/next when reaching limits
        model.currentIndex.addListener { _, _, newIndex ->
            previousBtn.disableProperty().set(newIndex == 0)
            nextBtn.disableProperty().set(newIndex == model.videoLength.get())
        }

        model.videoLength.addListener{ _, _, newVideoLength ->
            nextBtn.disableProperty().set(model.currentIndex.get() == newVideoLength)
        }

        val timeline = Timeline(model, mediaView)
        hbox.alignment = Pos.CENTER
        HBox.setHgrow(timeline, Priority.ALWAYS)
        hbox.children.addAll(previousBtn, timeline, nextBtn)

        hbox.minHeight = 50.0

        return hbox
    }

    private fun getSelectionPane() : Pane {
        val intensityPane = getIntensityPane()
        val positionPane = getPositionPane()
        val notesPane = getNotesPane()

        val hbox = HBox(20.0)
        val vbox = VBox(5.0)

        vbox.children.addAll(positionPane, notesPane)
        HBox.setMargin(intensityPane, Insets(0.0, 0.0, 5.0, 5.0))
        HBox.setMargin(vbox, Insets(0.0, 5.0, 5.0, 0.0))
        HBox.setHgrow(intensityPane, Priority.ALWAYS)
        hbox.children.addAll(intensityPane, vbox)

        return hbox
    }

    private fun getIntensityPane() : Node {
        val intensityToggleGroup = ToggleGroup()
        val hbox = HBox(5.0)
        val vboxLeft = VBox(2.0)
        val vboxRight = VBox(2.0)
        hbox.children.addAll(vboxLeft, vboxRight)


        model.intensities.forEachIndexed { index, intensity ->
            val intensityRadioBtn = RadioButton(intensity.toString())
            intensityRadioBtn.toggleGroup = intensityToggleGroup
            intensityRadioBtn.userData = intensity
            if (index > Math.floor(model.intensities.size / 2.0)) {
                vboxRight.children.add(intensityRadioBtn)
            } else {
                vboxLeft.children.add(intensityRadioBtn)
            }
        }

        intensityToggleGroup.selectedToggleProperty().addListener { _, _, selectedToggle ->
            if (selectedToggle != null) {
                val intensity: Intensity = selectedToggle.userData as Intensity
                model.currentIntensity.set(intensity.letter)
            }
        }

        model.currentIntensity.addListener { _, _, newVal ->
            for (toggle in intensityToggleGroup.toggles) {
                val intensity : Intensity = toggle.userData as Intensity

                if (intensity.letter == newVal) {
                    if (!toggle.isSelected) {
                        toggle.selectedProperty().set(true)
                    }
                } else {
                    toggle.selectedProperty().set(false)
                }
            }
        }

        val intensityPane = TitledPane(model.intensityAliasName.get(), hbox)
        intensityPane.collapsibleProperty().set(false)

        return intensityPane
    }

    private fun getPositionPane() : Node {
        val vbox = VBox(5.0)
        val positionTextField = TextField()
        positionTextField.editableProperty().set(false)
        positionTextField.focusTraversableProperty().set(false)
        //positionTextField.disableProperty().set(true)

        val positionLabel = Label(model.positions.map { it.toString() }.joinToString("    ") + "    (Backspace to remove)")
        vbox.children.addAll(positionLabel, positionTextField)
        val positionPane =  TitledPane(model.positionAliasName.get(), vbox)
        positionPane.collapsibleProperty().set(false)

        model.currentPosition.addListener {_, _, newVal ->
            positionTextField.text = newVal
        }

        return positionPane;
    }

    private fun getNotesPane() : Node {
        val notesTextArea = TextField()
        notesTextArea.prefHeight = 40.0
        val notesPane = TitledPane("Notes", notesTextArea)
        notesPane.collapsibleProperty().set(false)

        notesTextArea.textProperty().addListener { _, _, newText ->
            if (newText.contains("\"")) {
                val alert = Alert(AlertType.ERROR, "A note cannot contain the symbol '\"'. Please remove this symbol.", ButtonType.OK)
                alert.showAndWait()
            } else if (newText.contains("\n")) {
                val alert = Alert(AlertType.ERROR, "A note cannot contain new lines. Please remove the new line.", ButtonType.OK)
                alert.showAndWait()
            } else {
                model.currentNotes.set(newText)
            }
        }

        model.currentNotes.addListener { _, _, newText ->
            notesTextArea.text = newText
        }

        return notesPane
    }
}
