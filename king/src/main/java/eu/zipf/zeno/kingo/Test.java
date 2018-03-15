/**package eu.zipf.zeno.kingo;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;

public class Test {
    public static ArrayList<LocalPlayer> localPlayers;
    public Test() {

        Scanner sc = new Scanner(System.in);
        Dice dc = new Dice(6);
        localPlayers = new ArrayList<LocalPlayer>();
        LocalPlayer p1 = new LocalPlayer("Manu",0);
        localPlayers.add(p1);
        LocalPlayer p2 = new LocalPlayer("Zeno",one);
        localPlayers.add(p2);

        int pCnt = 0;
        boolean run = true;
        while (run) {
            pCnt%=two;
            if (localPlayers.get(pCnt).isInTokyo()) {
                localPlayers.get(pCnt).addScore(two);
            }
            if (localPlayers.get(pCnt).getScore() < 20) {
                System.out.println(localPlayers.get(pCnt).getName()+"s turn:");
                dc.roll();
                dc.print();
                for (int i = 0; i < two; i++) {
                    String in = sc.nextLine();
                    byte[] index = getIndex(in);
                    dc.reroll(index);
                    dc.print();
                }
                if (!localPlayers.get(pCnt).isInTokyo()) {
                    localPlayers.get(pCnt).heal(dc.getHeal());
                }
                if (dc.getDamage() > 0) {
                    if (localPlayers.get(pCnt).isInTokyo()) {
                        ArrayList<LocalPlayer> toDamage = (ArrayList<LocalPlayer>) localPlayers.clone();
                        toDamage.remove(localPlayers.get(pCnt));
                        Tokyo.dealDamage(toDamage, dc.getDamage());
                    } else if (!Tokyo.isEmpty()) {
                        Tokyo.getLocalPlayer().damage(dc.getDamage());
                        //Frage Spieler ob er Tokyo verlassen will
                        System.out.println(Tokyo.getLocalPlayer().getName()+ " do you want to leave Tokyo? Yes / No");
                        String mayLeave = sc.nextLine();
                        if (Objects.equals(mayLeave, "yes") || Objects.equals(mayLeave, "Yes") || Objects.equals(mayLeave, "YES")){
                            Tokyo.getLocalPlayer().leaveTokyo();
                            localPlayers.get(pCnt).joinTokyo();
                        }
                    } else {
                        localPlayers.get(pCnt).joinTokyo();
                    }
                }
				/*if (!Tokyo.isEmpty()) {
					System.out.println(Tokyo.getLocalPlayer().getName());
				}else{
					System.out.println("Tokyo is empty.");
				}*/

/**
                for(int i = 0; i< localPlayers.size(); i++){
                    LocalPlayer p = localPlayers.get(i);
                    System.out.println(p.getName()+"\tHP: "+p.getHp()+"\tScore: "+p.getScore());
                }
                if(localPlayers.size()<two){
                    System.out.println(localPlayers.get(0).getName()+" wins!");
                    run = false;
                }
                pCnt++;
            }else {
                System.out.println(localPlayers.get(pCnt).getName()+" wins!");
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
            ret[i] = (int) arr[i]-one;
        }
        return ret;
    }

    public static void main(String args[]) {
        new Test();
    }
}
**/