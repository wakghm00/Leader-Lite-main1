package leader.util.render.animations;

public class TimerUtil {
    public long lastMS;

    public long getCurrentMS() {
        return System.nanoTime() / 1000000L;
    }

    public boolean hasReached(double milliseconds) {
        if (milliseconds == 0) {
            return true;
        }
        return (double) (this.getCurrentMS() - this.lastMS) >= milliseconds;
    }

    public boolean hasTimeElapsed(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public void reset() {
        this.lastMS = this.getCurrentMS();
    }

    public long passed() {
        return this.getCurrentMS() - this.lastMS;
    }

    public long getTime() {
        return System.nanoTime() / 1000000L;
    }

    public TimerUtil delay(int delay) {
        this.lastMS = this.getCurrentMS() + delay;
        return this;
    }

    public void setTime(long time) {
        lastMS = time;
    }
}
