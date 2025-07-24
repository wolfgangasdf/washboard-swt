import org.eclipse.swt.graphics.Point;
import org.jetbrains.annotations.NotNull;

// https://slack-chats.kotlinlang.org/t/16111889/how-to-construct-java-sealed-class-in-kotlin-it-s-will-seale
class KPoint {
    static @NotNull Point instance(int x, int y) {
        return new Point(x, y);
    }
}