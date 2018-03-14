package eu.zipf.zeno.kingo;

import java.util.ArrayList;

public class LocalPlayer {
    private String name;
    private int hp, energy, score, maxHp, playerID;
    private ArrayList<Card> cards;
    private boolean inTokyo = false;
    //public boolean mayLeave;

    public LocalPlayer(String name, int playerID) {
        this.name = name;
        this.playerID = playerID;
        this.hp = 10;
        this.maxHp = 10;
        this.energy = 0;
        this.score = 0;
        this.cards = new ArrayList<Card>();
    }

    public void heal(int amount) {
        int _hp = this.hp+amount;
        if(_hp>=this.maxHp){
            _hp = this.maxHp;
        }
        this.hp = _hp;
    }

    public void damage(int amount) {
        this.hp -= amount;
        //mayLeave = true;
        if(this.hp<=0){
            this.kill();
        }
    }

    public void joinTokyo() {
        //this.mayLeave = false;
        this.inTokyo = true;
        Tokyo.setLocalPlayer(this);
        System.out.println(this.getName()+" joined Tokyo");
        addScore(1);
    }
    public void leaveTokyo(){
        this.inTokyo = false;
        Tokyo.setLocalPlayer(null);
        System.out.println(this.getName()+" left Tokyo");
    }

    public void kill(){

    }

    public String getName() {
        return this.name;
    }
    public int getHp(){
        return this.hp;
    }
    public boolean isInTokyo() {
        return this.inTokyo;
    }
    public void addScore(int _score){
        this.score += _score;
    }
    public int getScore() {
        return this.score;
    }
}
