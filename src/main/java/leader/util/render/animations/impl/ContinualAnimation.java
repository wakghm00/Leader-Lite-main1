package leader.util.render.animations.impl;


import leader.util.render.animations.Animation;
import leader.util.render.animations.Direction;

public class ContinualAnimation {
    private float output, endpoint;

    private Animation animation = new SmoothStepAnimation(0, 0, Direction.BACKWARDS);

    public void animate(float destination, int ms) {
        output = (float) (endpoint - animation.getOutput());
        endpoint = destination;
        if (output != (endpoint - destination)) {
            animation = new SmoothStepAnimation(ms, endpoint - output, Direction.BACKWARDS);
        }
    }


    public boolean isDone() {
        return output == endpoint || animation.isDone();
    }

    public float getOutput() {
        output = (float) (endpoint - animation.getOutput());
        return output;
    }

    public Animation getAnimation() {
        return animation;
    }
}
