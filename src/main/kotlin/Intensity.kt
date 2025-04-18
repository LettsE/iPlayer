import javafx.scene.input.KeyCode

class Intensity(name : String, letter : String, keycode : ArrayList<KeyCode>) {
    val name = name
    val letter = letter
    val keycodes = keycode

    override fun toString(): String {
        return "$letter: $name"
    }
}