import javafx.scene.input.KeyCode

class Position(name : String, letter : String, keycode : KeyCode) {
    val name = name
    val letter = letter
    val keycode = keycode

    override fun toString(): String {
        return "$letter: $name"
    }
}