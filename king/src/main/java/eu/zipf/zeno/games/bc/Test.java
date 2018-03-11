package eu.zipf.zeno.games.bc;

import android.widget.TextView;

import com.google.android.gms.games.multiplayer.Participant;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;

public class Test {
    public static ArrayList<Player> players;
    public Test() {

        Scanner sc = new Scanner(System.in);
        DiceSet dc = new DiceSet(6);
        players = new ArrayList<Player>();
        Player p1 = new Player("Manu");
        players.add(p1);
        Player p2 = new Player("Zeno");
        players.add(p2);

        int pCnt = 0;
        boolean run = true;
        while (run) {
            pCnt%=2;
            if (players.get(pCnt).isInTokyo()) {
                players.get(pCnt).addScore(2);
            }
            if (players.get(pCnt).getScore() < 20) {
                System.out.println(players.get(pCnt).getName()+"s turn:");
                dc.roll();
                dc.print();
                for (int i = 0; i < 2; i++) {
                    String in = sc.nextLine();
                    int[] index = getIndex(in);
                    dc.reroll(index);
                    dc.print();
                }
                if (!players.get(pCnt).isInTokyo()) {
                    players.get(pCnt).heal(dc.getHeal());
                }
                if (dc.getDamage() > 0) {
                    if (players.get(pCnt).isInTokyo()) {
                        ArrayList<Player> toDamage = (ArrayList<Player>) players.clone();
                        toDamage.remove(players.get(pCnt));
                        Tokyo.dealDamage(toDamage, dc.getDamage());
                    } else if (!Tokyo.isEmpty()) {
                        Tokyo.getPlayer().damage(dc.getDamage());
                        //Frage Spieler ob er Tokyo verlassen will
                        System.out.println(Tokyo.getPlayer().getName()+ " do you want to leave Tokyo? Yes / No");
                        String mayLeave = sc.nextLine();
                        if (Objects.equals(mayLeave, "yes") || Objects.equals(mayLeave, "Yes") || Objects.equals(mayLeave, "YES")){
                            Tokyo.getPlayer().leaveTokyo();
                            players.get(pCnt).joinTokyo();
                        }
                    } else {
                        players.get(pCnt).joinTokyo();
                    }
                }
				/*if (!Tokyo.isEmpty()) {
					System.out.println(Tokyo.getPlayer().getName());
				}else{
					System.out.println("Tokyo is empty.");
				}*/
                for(int i = 0;i<players.size();i++){
                    Player p = players.get(i);
                    System.out.println(p.getName()+"\tHP: "+p.getHp()+"\tScore: "+p.getScore());
                }
                if(players.size()<2){
                    System.out.println(players.get(0).getName()+" wins!");
                    run = false;
                }
                pCnt++;
            }else {
                System.out.println(players.get(pCnt).getName()+" wins!");
                run = false;
            }

        }

    }

    private int[] getIndex(String in) {
        char toParse = ' ';
        ArrayList<Integer> l = new ArrayList<Integer>();
        for (int i = 0; i < in.length(); i++) {
            if (in.charAt(i) != ',') {
                toParse = in.charAt(i);
                l.add(Character.getNumericValue((in.charAt(i))));
            }
        }
        Object[] ret = l.toArray();

        return parseArray(ret);
    }

    private int[] parseArray(Object[] arr) {
        int[] ret = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            ret[i] = (int) arr[i]-1;
        }
        return ret;
    }

    public static void main(String args[]) {
        new Test();
    }
}
