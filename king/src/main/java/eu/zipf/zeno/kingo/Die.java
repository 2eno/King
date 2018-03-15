package eu.zipf.zeno.kingo;

public class Die {
    public final static byte ONE = 1, TWO = 2, THREE = 3, SMASH = 4, ENERGY = 5, HEAL = 6;
    public String[] names ={"one","two","three","SMASH","ENERGY","HEAL"};

    private byte score;

    public Die() {
        this.score = 0;
    }

    public void roll() {
        this.score = (byte) (Math.random() * 6 + ONE);
    }

    public byte getScore() {
        return this.score;
    }
    public String getName(){
        return names[this.score-ONE];
    }

}
