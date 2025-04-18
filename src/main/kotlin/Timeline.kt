import javafx.animation.KeyFrame
import javafx.beans.Observable
import javafx.geometry.VPos
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.ScrollBar
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.scene.media.MediaView
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import javafx.util.Duration

class Timeline(model: Model, mediaView: MediaView?) : Pane() {
    private val model = model
    private val mediaView = mediaView
    private val SEC_WIDTH = 3.0 // Width in pixels of a second

    private val  completeColor = Color.rgb(68,116,157)
    private val  incompleteColor = Color.rgb(198,212,225)
    private val  noteColor = Color.rgb(250,202,102)

    private val canvas = Canvas()
    private val scrollBar = ScrollBar()

    init {
        canvas.height = 35.0
        model.videoLength.addListener { _, _, newLength ->
            draw()
        }

        widthProperty().addListener { _, _, _ -> draw() }
        heightProperty().addListener { _, _, _ -> draw() }

        scrollBar.valueProperty().addListener { _, _, _ ->
            draw()
        }

        model.currentIndex.addListener { _, _, _ ->
            draw()
        }

        this.setOnMouseClicked { event ->
            val index = getStartIdx() + Math.floor(event.x / SEC_WIDTH)

            if (model.loopVideo.get()) {
                model.currentIndex.set(index.toInt())
            } else {
                mediaView?.mediaPlayer?.seek(Duration(index*1000))
            }
        }

        val layout = VBox()
        layout.children.addAll(canvas, scrollBar)
        canvas.widthProperty().bind(this.widthProperty())
        this.children.add(layout)
    }

    private fun getStartIdx() : Int {
        return scrollBar.value.toInt();
    }

    /**
     * Gives a unique type to entries
     */
    private fun getEntryType(entry : Model.Entry) : Int {
        var type = 0

        if (entry.notes.isNotEmpty()) {
            type += 1
        }

        if (entry.intensity.isNotEmpty()) {
            type += 10
        }

        if (entry.position.isNotEmpty()) {
            type += 100
        }
        return type
    }

    private fun drawEntry(gc : GraphicsContext, entry : Model.Entry, x : Double, width : Double) {
        if (entry.intensity.isNotEmpty() || entry.position.isNotEmpty()) {
            gc.fill = incompleteColor
            if (entry.intensity.isNotEmpty() && entry.position.isNotEmpty()) {
                gc.fill = completeColor
            }
            gc.fillRect(x, 0.0, width, this.height)
        }

        if (entry.notes.isNotEmpty()) {
            gc.fill = noteColor
            gc.fillRect(x, 0.0, width, this.height/4.0)
        }
    }

    fun draw() {
        val gc = this.canvas.graphicsContext2D
        gc.fill = Color.LIGHTGRAY
        gc.fillRect(0.0, 0.0, this.canvas.width, this.canvas.height)

        val visibleAmount = (this.canvas.width/SEC_WIDTH).toInt()
        val startIdx = getStartIdx()
        val endIdx = Math.min(startIdx+visibleAmount, model.history.size)

        scrollBar.visibleAmount = visibleAmount.toDouble()
        scrollBar.min = 0.0
        scrollBar.max = (model.history.size).toDouble()
        scrollBar.unitIncrement = 1.0
        scrollBar.blockIncrement = 1.0

        if (endIdx > startIdx) {
            var lastX = 0.0
            var width = 0.0
            var lastType = 0
            var lastEntry: Model.Entry? = null
            gc.fill = Color.WHITE
            gc.fillRect(0.0, 0.0, (endIdx-startIdx)*SEC_WIDTH, this.height)

            for (i in startIdx until endIdx) {
                val entry = model.history[i]
                val entryType = getEntryType(entry)

                if (entryType == lastType) {
                    // Optimization: We group adjacent rectangles to reduce the number of calls to #fillRect
                    width += SEC_WIDTH
                } else {
                    if (lastEntry != null) {
                        drawEntry(gc, lastEntry, lastX, width)
                    }
                    lastX = (i - startIdx) * SEC_WIDTH
                    width = SEC_WIDTH
                }
                lastType = entryType
                lastEntry = entry
            }

            lastEntry?.let { drawEntry(gc, it, lastX, width) }

            // Draw time markers
            gc.textBaseline = VPos.TOP
            gc.textAlign  = TextAlignment.CENTER
            gc.fill = Color.BLACK
            for (i in startIdx until endIdx) {
                var x = (i - startIdx) * SEC_WIDTH + SEC_WIDTH/2
                if (i % 60 == 0) {
                    gc.fillRect(x, 0.0, 1.0, 7.0)
                    gc.fillText("" + (i/60), x, 7.0)
                } else if (i % 5 == 0) {
                    gc.fillRect(x, 0.0, 1.0, 3.0)
                }
            }
        }


        // Draw cursor
        val cursorWidth = 1
        gc.fill = Color.RED

        val xPos = DoubleArray(3)
        val yPos = DoubleArray(3)
        val left = (model.currentIndex.get()-startIdx)*SEC_WIDTH
        val mid = left+(SEC_WIDTH-cursorWidth)/2
        val right = left + SEC_WIDTH

        xPos[0] = left-2; yPos[0] = 0.0
        xPos[1] = mid; yPos[1] = 7.0
        xPos[2] = right+2; yPos[2] = 0.0

        gc.fillPolygon(xPos, yPos, 3)
        gc.fillRect(mid, 0.0, 1.0, this.height)
    }
}