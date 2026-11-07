package chess;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.Random;

/** 用 PCM 实时合成的轻量音效，不依赖任何外部音频文件。 */
public final class Sound {

    public static boolean enabled = true;

    private static final float RATE = 22050f;
    private static final AudioFormat FMT = new AudioFormat(RATE, 8, 1, true, false);

    private Sound() {}

    public static void play(String type) {
        if (!enabled) return;
        final byte[] data;
        switch (type) {
            case "capture":
                data = render(new float[]{230, .13f, .95f, .45f}, new float[]{520, .09f, .55f, .20f});
                break;
            case "check":
                data = render(new float[]{740, .11f, .75f, .05f}, new float[]{990, .17f, .80f, .05f});
                break;
            case "win":
                data = render(new float[]{523, .13f, .70f, .02f}, new float[]{659, .13f, .70f, .02f},
                        new float[]{784, .13f, .72f, .02f}, new float[]{1046, .28f, .80f, .02f});
                break;
            case "lose":
                data = render(new float[]{420, .16f, .70f, .04f}, new float[]{310, .30f, .70f, .04f});
                break;
            case "illegal":
                data = render(new float[]{150, .13f, .60f, .10f});
                break;
            case "select":
                data = render(new float[]{620, .045f, .35f, .08f});
                break;
            default: // move
                data = render(new float[]{190, .085f, .85f, .40f});
                break;
        }
        Thread t = new Thread(() -> {
            SourceDataLine line = null;
            try {
                line = AudioSystem.getSourceDataLine(FMT);
                line.open(FMT, 8192);
                line.start();
                line.write(data, 0, data.length);
                line.drain();
            } catch (Exception ignored) {
                // 没有音频设备时静默忽略，不影响游戏
            } finally {
                if (line != null) { line.stop(); line.close(); }
            }
        }, "xiangqi-sound");
        t.setDaemon(true);
        t.start();
    }

    /** 每段参数：{频率, 时长(秒), 音量, 噪声比例} */
    private static byte[] render(float[]... segs) {
        int total = 0;
        for (float[] s : segs) total += (int) (s[1] * RATE);
        byte[] out = new byte[total];
        Random rnd = new Random(20261107L);
        int idx = 0;
        for (float[] s : segs) {
            int n = (int) (s[1] * RATE);
            float freq = s[0], amp = s[2], noise = s[3];
            for (int i = 0; i < n; i++) {
                float t = i / RATE;
                float env = (float) Math.exp(-t * 22.0) * (1.0f - (float) i / n);
                double tone = Math.sin(2 * Math.PI * freq * t) * 0.75
                        + Math.sin(2 * Math.PI * freq * 2.03 * t) * 0.18;
                double v = tone * (1 - noise) + (rnd.nextDouble() * 2 - 1) * noise;
                int iv = (int) (v * env * amp * 105);
                out[idx++] = (byte) Math.max(-127, Math.min(127, iv));
            }
        }
        return out;
    }
}
