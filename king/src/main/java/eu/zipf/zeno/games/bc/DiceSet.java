package eu.zipf.zeno.games.bc;

public class DiceSet {
    private Dice[] diceSet;
    private int size;

    public DiceSet(int size) {
        this.size = size;
        this.diceSet = new Dice[size];
        for (int i = 0; i < diceSet.length; i++) {
            diceSet[i] = new Dice();
        }
    }

    public void roll() {
        for (int i = 0; i < diceSet.length; i++) {
            diceSet[i].roll();
        }
    }

    public byte[] getScores() {
        byte[] ret = new byte[diceSet.length];
        for (int i = 0; i < diceSet.length; i++) {
            ret[i] = diceSet[i].getScore();
        }
        return ret;
    }

    public String[] getNames() {
        String[] ret = new String[diceSet.length];
        for (int i = 0; i < diceSet.length; i++) {
            ret[i] = diceSet[i].getName();
        }
        return ret;
    }

    public void print() {
        String out = "";
        for (int i = 0; i < diceSet.length; i++) {
            out += (diceSet[i].getName() + ", ");
        }
        out = out.substring(0, out.length() - 2);
        System.out.println(out);
    }

    public int getSize() {
        return this.size;
    }

    public void reroll(int[] index) {
        for (int i = 0; i < index.length; i++) {
            if (index[i] > diceSet.length) {
                System.err.println("OUT OF BOUNDS@reroll");
                return;
            }
            diceSet[index[i]].roll();
        }
    }

    public int getHeal() {
        int cnt = 0;
        for (int i = 0; i < diceSet.length; i++) {
            if (diceSet[i].getScore() == Dice.HEAL) {
                cnt++;
            }
        }
        return cnt;
    }

    public int getDamage() {
        int cnt = 0;
        for (int i = 0; i < diceSet.length; i++) {
            if (diceSet[i].getScore() == Dice.SMASH) {
                cnt++;
            }
        }
        return cnt;
    }
}
