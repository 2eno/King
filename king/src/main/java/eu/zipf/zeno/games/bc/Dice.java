package eu.zipf.zeno.games.bc;

public class Dice {
    public static byte ONE = 1, TWO = 2, THREE = 3, SMASH = 4, ENERGY = 5, HEAL = 6;
    public String[] names ={"1","2","3","SMASH","ENERGY","HEAL"};

    private byte score;

    public Dice() {
        this.score = 0;
    }

    public void roll() {
        this.score = (byte) (Math.random() * 6 + 1);
    }

    public byte getScore() {
        return this.score;
    }
    public String getName(){
        return names[this.score-1];
    }

}
