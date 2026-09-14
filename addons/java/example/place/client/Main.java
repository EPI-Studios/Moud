import com.meekdev.moud.addon.java.PlaceScript;

public class Main extends PlaceScript {

    private int frames;
    private double seconds;

    @Override
    public void run() {
        onRenderStep(dt -> {
            frames++;
            seconds += dt;
            if (seconds >= 5) {
                print("java client at " + Math.round(frames / seconds) + " frames a second");
                frames = 0;
                seconds = 0;
            }
        });
        print("java client place running");
    }
}
