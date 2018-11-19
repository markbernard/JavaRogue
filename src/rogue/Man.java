package rogue;

import java.io.*;
import java.util.Date;
import java.awt.*;


/**
 *
 */
public class Man extends Persona implements Serializable {
    private static final long serialVersionUID = 6850118418880209819L;

    Rogue self;
    View view;
    Option option;
    ItemList pack = null;
    int expPoints = 0; // Experience points
    int movesLeft = 1250; // Food counter
    int lessHp = 0;
    int mMoves = 0; // General move counter

    boolean sustainStrength = false;
    boolean detectMonster = false;
    boolean passgo = false;
    boolean rTeleport = false;
    boolean jump = false;
    boolean seeInvisible;
    static boolean gameOver = false;
    static boolean savedGame = false;
    static String gameOverMessage = "";
    boolean wizzed = false;
    static int justLoaded = 0;

    int regeneration = 0;
    boolean rSeeInvisible = false;
    boolean maintainArmor = false;

    static final int R_TELE_PERCENT = 8;

    int autoSearch = 0; // Number of times to auto-search each turn

    String newLevelMessage;
    String hungerStr = "";
    boolean trapDoor;

    char[][] seen; // Set seen to 1(stairs-floor) 2(walls) 0(other)
                   // Also 4=just seen 8=last seen
                   // Bits 4-6 are the wally code below
    static final char WALLYS[] = { ' ', '|', '-', '+', '#', '%', '^' };

    static final int MOVED = 0;
    static final int MOVE_FAILED = -1;
    static final int STOPPED_ON_SOMETHING = -2;

    static final int HUNGRY = 300;
    static final int WEAK = 150;
    static final int FAINT = 20;
    static final int STARVE = 0;

    static final int MAX_EXP_LEVEL = 21;
    static final int MAX_EXP = 10000001;
    static final int MAX_GOLD = 999999;
    static final int MAX_ARMOR = 99;
    static final int MAX_HP = 999;
    static final int MAX_STRENGTH = 99;

    static final int MAX_PACK_COUNT = 24;

    Man(Rogue self, View view) {
        super(self);
        mt = Monster.MONSTER_TABLE[Monster.MONSTERS - 1];
        ichar = (char) (mt.ichar | U_ROGUE);
        this.self = self;
        this.option = new Option();
        this.view = view;
        hpMax = hpCurrent = mt.hpCurrent;
        strMax = strCurrent = 16;
        exp = 1;
    }

    void rest(int count) {
        self.interrupted = false;
        do {
            if (self.interrupted) {
                break;
            }
            regMove();
        } while (--count > 0);
    }

    private void turnPassage(int dir, boolean fast) {
        int crow = row, ccol = col, turns = 0;
        int ndir = 0;

        if ((dir != 'h') && level.canTurn(crow, ccol + 1)) {
            turns++;
            ndir = 'l';
        }
        if ((dir != 'l') && level.canTurn(crow, ccol - 1)) {
            turns++;
            ndir = 'h';
        }
        if ((dir != 'k') && level.canTurn(crow + 1, ccol)) {
            turns++;
            ndir = 'j';
        }
        if ((dir != 'j') && level.canTurn(crow - 1, ccol)) {
            turns++;
            ndir = 'k';
        }
        if (turns == 1) {
            playMove(ndir - (fast ? 32 : 96), 1);
        }
    }

    boolean search(int n, boolean is_auto) {
        int found = 0, shown = 0;
        for (int r = row - 1; r <= row + 1; r++) {
            if (r >= MIN_ROW && r < level.nrow - 1) {
                for (int c = col - 1; c <= col + 1; c++) {
                    if (c >= 0 && c < level.ncol) {
                        if (0 != (level.map[r][c] & HIDDEN)) {
                            ++found;
                        }
                    }
                }
            }
        }
        do {
            for (int r = row - 1; r <= row + 1; r++) {
                if (r >= MIN_ROW && r < level.nrow - 1) {
                    for (int c = col - 1; c <= col + 1; c++) {
                        if (c >= 0 && c < level.ncol) {
                            if (0 != (level.map[r][c] & HIDDEN) && self.rand.percent(17 + exp + ringExp)) {
                                level.map[r][c] &= ~HIDDEN;
                                if (blind == 0 && (r != row || c != col)) {
                                    view.mark(r, c);
                                }
                                shown++;
                                if (0 != (level.map[r][c] & TRAP)) {
                                    Trap t = (Trap) level.levelTraps.itemAt(r, c);
                                    if (t != null && t.kind < Trap.name.length) {
                                        tell(Trap.name[t.kind], true);
                                    } else {
                                        System.out.println("Err in search=flag=" + r + " " + c);
                                    }
                                }
                                if ((shown == found && found > 0) || self.interrupted) {
                                    break;
                                }
                                /* A search is half a move */
                                if (!is_auto && 0 == (n & 1)) {
                                    regMove();
                                }
                            }
                        }
                    }
                }
            }
        } while (--n > 0);

        return shown > 0;
    }

    int playMove(int ch, int count) {
        System.out.println((char) ch + " " + ch + " " + count);
        switch (ch) {
            case '.':
            case '-':
                rest(count);
                break;
            case 's':
                if (search(count, false)) {
                    vizset();
                    moveSeen();
                }
                break;
            case 'i':
                pack.inventory(Id.ALL_TOYS, view.msg, false);
                break;
            case 'f':
                // fight(0);
                break;
            case 'F':
                // fight(1);
                break;
            case Event.HOME:
            case Event.END:
            case Event.PGUP:
            case Event.PGDN:
                if (ch == Event.HOME) {
                    ch = 'y';
                }
                if (ch == Event.END) {
                    ch = 'b';
                }
                if (ch == Event.PGUP) {
                    ch = 'u';
                }
                if (ch == Event.PGDN) {
                    ch = 'n';
                }
            case Event.DOWN:
            case Event.UP:
            case Event.RIGHT:
            case Event.LEFT:
                if (ch == Event.DOWN) {
                    ch = 'j';
                }
                if (ch == Event.UP) {
                    ch = 'k';
                }
                if (ch == Event.RIGHT) {
                    ch = 'l';
                }
                if (ch == Event.LEFT) {
                    ch = 'h';
                }
            case 'h':
            case 'j':
            case 'k':
            case 'l':
            case 'y':
            case 'u':
            case 'n':
            case 'b':
                System.out.println("play_move - 1");
                oneMoveRogue(ch, true);
                System.out.println("play_move - 2");
                break;
            case 'H':
            case 'J':
            case 'K':
            case 'L':
            case 'B':
            case 'Y':
            case 'U':
            case 'N':
                while (!self.interrupted && oneMoveRogue((ch + 32), true) == MOVED) {
                    if (!self.interrupted && passgo && 0 != (level.map[row][col] & TUNNEL)) {
                        turnPassage(ch + 32, true);
                    }
                }
                break;
            /*
             * case '\010': case '\012': case '\013': case '\014': case '\031':
             * case '\025': case '\016': case '\002': do { r= row; c= col; m=
             * one_move_rogue(ch + 96, true); if(m==MOVE_FAILED ||
             * m==STOPPED_ON_SOMETHING || self.interrupted) break; try {
             * Thread.sleep(250); } catch (InterruptedException e){ } }
             * while(!next_to_something(r, c)); if(!self.interrupted && passgo
             * && m==MOVE_FAILED && 0!=(level.map[row][ col]&TUNNEL))
             * turn_passage(ch + 96, false); break;
             */
            case 'e':
                eat();
                break;
            case 'q':
                quaff();
                break;
            case 'r':
                readScroll();
                break;
            case 'm':
                move_onto();
                break;
            case ',':
                kickIntoPack();
                break;
            case 'd':
                drop();
                break;
            case 'P':
                putOnRing();
                break;
            case 'R':
                removeRing();
                break;
            case 'P' - '@': /* Print old messages */
                do {
                    view.msg.remessage(count++);
                    ch = self.rgetchar();
                } while (ch == 'P' - '@');
                view.msg.checkMessage();
                count = playMove(ch, 0);
                break;
            case 'W' - '@':
                tell((wizard = !wizard) ? "Welcome, wizard!" : "not wizard anymore");
                wizzed = true;
                Id.wizardIdentify();
                break;
            case 'R' - '@':
                view.repaint(30);
                break;
            case '>':
                if (wizard) {
                    return -1;
                }
                if (0 != (level.map[row][col] & STAIRS)) {
                    if (levitate != 0) {
                        tell("you're floating in the air!");
                    } else {
                        return -1;
                    }
                }
                return 0;
            case '<':
                if (!wizard) {
                    if (0 == (level.map[row][col] & STAIRS)) {
                        tell("I see no way up");
                        return 0;
                    }
                    if (!hasAmulet()) {
                        tell("your way is magically blocked");
                        return 0;
                    }
                }
                newLevelMessage = "you feel a wrenching sensation in your gut";
                if (level.currentLevel == 1) {
                    win();
                } else {
                    level.currentLevel -= 2;
                }
                
                return -1;
            case ')':
                tell(weapon == null ? "not wielding anything" : pack.singleInv(weapon.ichar));
                break;
            case ']':
                tell(armor == null ? "not wearing anything" : pack.singleInv(armor.ichar));
                break;
            case '=':
                if (leftRing == null && rightRing == null) {
                    tell("not wearing any rings");
                }
                if (leftRing != null) {
                    tell(pack.singleInv(leftRing.ichar));
                }
                if (rightRing != null) {
                    tell(pack.singleInv(rightRing.ichar));
                }
                break;
            case '^':
                idTrap();
                break;
            case '/':
                Id.idType(this);
                break;
            case '?':
                idCom();
                break;
            case '!':
                // do_shell();
                break;
            case 'o':
                option.editOpts(this);
                break;
            case 'I':
                // single_inv(0);
                break;
            case 'T':
                takeOff();
                break;
            case 'W':
                wear();
                break;
            case 'w':
                wield();
                break;
            case 'c':
                callIt();
                break;
            case 'z':
                zapp();
                break;
            case 't':
                throwMissile();
                break;
            case 'v':
                tell("java rogue clone", false);
                break;
            case 'Q':
                if (option.askQuit) {
                    if (!view.msg.yesOrNo("Really quit?"))
                        break;
                }
                killedBy(null, Monster.QUIT);
                break;
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                view.refresh();
                do {
                    if (count < 100)
                        count = 10 * count + ch - '0';
                    ch = self.rgetchar();
                } while ('0' <= ch && ch <= '9');
                if (ch != '\033') {
                    count = playMove(ch, count);
                }
                break;
            case ' ':
                break;
            case '\001':
                // show_average_hp();
                break;
            case 'S':
                saveGame();
                break;
            default:
                if (!wizard)
                    ch = 0;
                switch (ch) {
                    case '@':
                        System.out.println(this.toString());
                        for (int i = row - 1; i <= row + 1; i++) {
                            for (int j = col - 1; j <= col + 1; j++) {
                                try {
                                    System.out.print(Integer.toString(07000000 + level.map[i][j], 8) + view.buffer[i][j] + " ");
                                } catch (Exception e) {
                                    System.out.print("-------? ");
                                }
                            }
                            System.out.println("");
                        }
                        tell("At row " + row + ", column " + col);
                        break;
                    case 'B' - '@':
                        level.levelToys.inventory(Id.ALL_TOYS, view.msg, false);
                        break;
                    case 'D' - '@':
                        System.out.println("Monsters:");
                        for (int i = 0; i < level.levelMonsters.size(); i++)
                            System.out.println(level.levelMonsters.get(i));
                        break;
                    case 'F' - '@':
                        level.currentLevel += 19;
                        return -1; // plummet
                    case 'S' - '@':
                        level.drawMagicMap(this);
                        break;
                    case 'E' - '@':
                        level.showTraps(this);
                        break;
                    case 'O' - '@':
                        level.showToys(this);
                        break;
                    case 'C' - '@':
                        cToyForWizard();
                        break;
                    case 'X' - '@':
                        monsterForWizard();
                        break;
                    case 'Q' - '@':
                        level.showMonsters(this);
                        break;
                    case 'T' - '@':
                        tele();
                        break;
                    case 'W' - '@':
                        level.wanderer();
                        break;
                    case 'U' - '@':
                        level.unhide();
                        break;
                    default:
                        tell("unknown_command");
                        break;
                }
        }
        return count;
    }

    void play_level() {
        int ch;
        int count = 0;
        initSeen();
        view.mark(row, col);
        do {
            System.out.println("processing command");
            try {
                System.out.println("here - 1");
                self.interrupted = false;
                if (hitMessage.length() > 1) {
                    tell(hitMessage);
                    hitMessage = "";
                }
                System.out.println("here - 2");
                if (trapDoor) {
                    trapDoor = false;
                    break;
                }
                System.out.println("here - 3");
                showmap();
                System.out.println("here - 4");
                view.refresh();
                System.out.println("here - 5");

                ch = self.rgetchar();
                System.out.println("here - 6");
                if (!gameOver) {
                    view.msg.checkMessage();
                }
                System.out.println("here - 7");
                count = playMove(ch, 0);
                System.out.println("here - 8");
            } catch (Exception e) {
                if (e.getMessage() != null) {
                    System.out.println(e.getMessage());
                }
                e.printStackTrace();
            }
            System.gc();
            System.out.println(count);
        } while (count >= 0 && !gameOver);
    }

    char showrc(int r, int c) { // What man sees at this position
        char ch = 0;

        if (r == row && c == col) {
            return ichar;
        }

        if (blind == 0 && canSee(r, c)) {
            int mask = level.map[r][c];
            if (0 != (mask & DARK)) {
                if (r < row - 1 || r > row + 1 || c < col - 1 || c > col + 1) {
                    return ' ';
                }
            }
            if (0 != (mask & MONSTER)) {
                Monster monster = (Monster) level.levelMonsters.itemAt(r, c);
                ch = (char) (monster != null ? monster.gmc(this) : '$');
            } else if (0 != (mask & TOY)) {
                if (halluc == 0) {
                    Toy t = (Toy) level.levelToys.itemAt(r, c);
                    if (t == null) {
                        System.out.println("See no toy at " + new Rowcol(r, c));
                    } else {
                        ch = t.ichar;
                    }
                } else {
                    ch = (char) Id.getRandomObjectCharacter(self.rand);
                }
            } else {
                ch = level.getChar(r, c);
            }
        } else if (detectMonster && 0 != (level.map[r][c] & MONSTER) && blind == 0) {
            Monster monster = (Monster) level.levelMonsters.itemAt(r, c);
            ch = (char) (monster != null ? monster.gmc(this) : '$');
            if (self.rand.percent(30)) {
                detectMonster = false;
            }
        } else {
            ch = WALLYS[seen[r][c] >> 4];
        }
        // Error if blind and toy in tunnel or on door--see blank instead of
        // tunnel floor/door
        return ch;
    }

    void showmap() {
        Rowcol pt;
        while ((pt = view.getmark()) != null) {
            if (pt.col == 0 && pt.row == 0) {
                for (int r = Level.MIN_ROW; r < level.nrow - 1; r++) {
                    for (int c = 0; c < level.ncol; c++) {
                        view.addch(r, c, showrc(r, c));
                    }
                }
                break;
            } else {
                char ch = showrc(pt.row, pt.col);
                view.addch(pt.row, pt.col, ch);
            }
        }
    }

    boolean checkHunger(boolean messageOnly) {
        int i, n;
        boolean fainted = false;

        if (movesLeft == HUNGRY) {
            hungerStr = "hungry";
            tell("you feel " + hungerStr);
            printStat();
        }
        if (movesLeft == WEAK) {
            hungerStr = "weak  ";
            tell("you feel " + hungerStr, true);
            printStat();
        }
        if (movesLeft <= FAINT) {
            if (movesLeft == FAINT) {
                hungerStr = "faint ";
                tell("you " + hungerStr, true);
                printStat();
            }
            n = self.rand.get(FAINT - movesLeft);
            if (n > 0) {
                fainted = true;
                if (self.rand.percent(40)) {
                    movesLeft++;
                }
                tell("you faint", true);
                for (i = 0; i < n; i++) {
                    if (self.rand.coin()) {
                        level.moveMonsters(this);
                    }
                }
                tell("you can move again", true);
            }
        }
        if (messageOnly) {
            return fainted;
        }
        if (movesLeft <= STARVE) {
            killedBy(null, Monster.STARVATION);
            return false;
        }
        switch (eRings) {
            case -1:
                movesLeft -= (movesLeft % 2);
                break;
            case 0:
                movesLeft--;
                break;
            case 1:
                movesLeft--;
                checkHunger(true);
                movesLeft -= (movesLeft % 2);
                break;
            case 2:
                movesLeft--;
                checkHunger(true);
                movesLeft--;
                break;
        }
        
        return fainted;
    }

    boolean regMove() {
        boolean fainted = false;

        if ((movesLeft <= HUNGRY) || level.currentLevel >= level.maxLevel) {
            fainted = checkHunger(false);
        }
        level.moveMonsters(this);

        if (++mMoves >= 120) {
            mMoves = 0;
            level.wanderer();
        }
        super.regMove();
        heal();

        if (autoSearch > 0) {
            search(autoSearch, true);
        }
        
        return fainted;
    }

    void move_onto() {
        int ch;
        if (-2 == Id.isDirection(ch = self.rgetchar())) {
            tell("direction? ");
            ch = self.rgetchar();
        }
        view.msg.checkMessage();
        if (ch != '\033') {
            oneMoveRogue(ch, false);
        }
    }

    int oneMoveRogue(int dirch, boolean pickup) {
        System.out.println("one_move_rogue - 1");
        if (confused != 0) {
            dirch = movConfused();
        }
        System.out.println("one_move_rogue - 2");
        int d = Id.isDirection(dirch);
        System.out.println("one_move_rogue - 3");
        Rowcol pto = level.getDirRc(d, row, col, true);
        System.out.println("one_move_rogue - 4");

        if (!level.canMove(row, col, pto.row, pto.col)) {
            return MOVE_FAILED;
        }

        if (beingHeld || bearTrap > 0) {
            if (0 == (level.map[pto.row][pto.col] & MONSTER)) {
                if (beingHeld) {
                    tell("you are being held", true);
                } else {
                    tell("you are still stuck in the bear trap");
                    regMove();
                }

                return MOVE_FAILED;
            }
        }
        if (rTeleport) {
            if (self.rand.percent(R_TELE_PERCENT)) {
                tele();
                
                return STOPPED_ON_SOMETHING;
            }
        }
        if (0 != (level.map[pto.row][pto.col] & MONSTER)) {
            Monster monster = (Monster) level.levelMonsters.itemAt(pto.row, pto.col);
            if (monster != null) {
                rogueHit(monster, false);
            }
            regMove();
            
            return MOVE_FAILED;
        }
        if (0 != (level.map[pto.row][pto.col] & DOOR) && 0 != (level.map[row][col] & TUNNEL)) {
            level.wakeRoom(this, true, pto.row, pto.col);
        } else if (0 != (level.map[pto.row][pto.col] & TUNNEL) && 0 != (level.map[row][col] & DOOR)) {
            level.wakeRoom(this, false, row, col);
        }
        //////////////////////////////////////////////////
        if (blind == 0) { // Basic tunnel view
            for (int r = row - 1; r <= row + 1; r++) {
                for (int c = col - 1; c <= col + 1; c++) {
                    //// if(0!=(level.map[r][c]&TUNNEL) &&
                    //// 0==(level.map[r][c]&HIDDEN))
                    view.mark(r, c);
                }
            }
        }
        placeAt(pto.row, pto.col, MAN); // Note--sets row,col to pto
        if (blind == 0) { // Basic tunnel view
            for (int r = row - 1; r <= row + 1; r++) {
                for (int c = col - 1; c <= col + 1; c++) {
                    //// if(0!=(level.map[r][c]&TUNNEL) &&
                    //// 0==(level.map[r][c]&HIDDEN))
                    view.mark(r, c);
                }
            }
        }
        vizset();
        moveSeen();
        if (!jump) {
            showmap();
            view.refresh();
        }
        //////////////////////////////////////////////////
        Toy obj = null;
        boolean sos = false; // Stopped on something
        if (0 != (level.map[row][col] & TOY)) {
            if (levitate > 0 && pickup) {
                return STOPPED_ON_SOMETHING;
            }
            if (pickup && 0 == levitate) {
                obj = pickUp();
            }
            if (obj == null) {
                obj = (Toy) level.levelToys.itemAt(row, col);
                if (obj != null) {
                    tell("moved onto " + obj.getDesc());
                }
            } else if (obj.ichar == 1) { // Not a dusted scroll
                return STOPPED_ON_SOMETHING;
            }
            sos = true;
        }
        if (0 != (level.map[row][col] & (Level.DOOR | Level.STAIRS | Level.TRAP))) {
            if (levitate == 0 && 0 != (level.map[row][col] & Level.TRAP)) {
                trapPlayer();
            }
            sos = true;
        }
        return (regMove() /* fainted from hunger */
                || sos /* already on something */
                || confused != 0) ? STOPPED_ON_SOMETHING : MOVED;
    }

//    private boolean next_to_something(int drow, int dcol) {
//        int pass_count = 0;
//        int s;
//
//        if (confused != 0) {
//            return true;
//        }
//        if (blind > 0) {
//            return false;
//        }
//        int i_end = (row < (level.nrow - 2)) ? 1 : 0;
//        int j_end = (col < (level.ncol - 1)) ? 1 : 0;
//
//        for (int i = row > MIN_ROW ? -1 : 0; i <= i_end; i++) {
//            for (int j = col > 0 ? -1 : 0; j <= j_end; j++) {
//                if (i == 0 && j == 0) {
//                    continue;
//                }
//                if (row + i == drow && col + j == dcol) {
//                    continue;
//                }
//                int r = row + i;
//                int c = col + j;
//                s = level.map[r][c];
//                if (0 != (s & HIDDEN)) {
//                    continue;
//                }
//
//                /*
//                 * If the rogue used to be right, up, left, down, or right of
//                 * r,c, and now isn't, then don't stop
//                 */
//                if (0 != (s & (MONSTER | TOY | STAIRS))) {
//                    if ((r == drow || c == dcol) && !(r == row || c == col)) {
//                        continue;
//                    }
//                    
//                    return true;
//                }
//                if (0 != (s & TRAP)) {
//                    if ((r == drow || c == dcol) && !(r == row || c == col)) {
//                        continue;
//                    }
//                    
//                    return true;
//                }
//                if ((i - j == 1 || i - j == -1) && 0 != (s & TUNNEL)) {
//                    if (++pass_count > 1) {
//                        return true;
//                    }
//                }
//                if (0 != (s & DOOR) && (i == 0 || j == 0)) {
//                    return true;
//                }
//            }
//        }
//        
//        return false;
//    }

    Trap trapPlayer() { // Call with the trap list
        level.map[row][col] &= ~HIDDEN;
        if (self.rand.percent(exp + ringExp)) {
            tell("the trap failed", true);
            return null;
        }
        Trap t = (Trap) level.levelTraps.itemAt(row, col);
        if (t == null) {
            return null;
        }
        switch (t.kind) {
            case Trap.BEAR_TRAP:
                tell(t.trapMessage(this), true);
                bearTrap = self.rand.get(4, 7);
                t = null;
                break;
            case Trap.TRAP_DOOR:
                trapDoor = true;
                newLevelMessage = t.trapMessage(this);
                break;
            case Trap.TELE_TRAP:
                view.mark(row, col);
                tele();
                break;
            case Trap.DART_TRAP:
                tell(t.trapMessage(this), true);
                hpCurrent -= Id.getDamage("1d6", self.rand);
                if (hpCurrent <= 0) {
                    hpCurrent = 0;
                }
                if (!sustainStrength && self.rand.percent(40) && strCurrent >= 3) {
                    strCurrent--;
                }
                printStat();
                if (hpCurrent <= 0) {
                    killedBy(null, Monster.POISON_DART);
                }
                break;
            case Trap.SLEEPING_GAS_TRAP:
                tell(t.trapMessage(this), true);
                takeANap();
                break;
            case Trap.RUST_TRAP:
                tell(t.trapMessage(this), true);
                rust(null);
                break;
        }
        return t;
    }

    void takeANap() {
        int i = self.rand.get(2, 5);
        self.mdSleep(1000);
        while (--i >= 0) {
            level.moveMonsters(this);
        }
        self.mdSleep(1000);
        tell("you can move again");
    }

    /*
     * Level: 99 Gold: 999999 Hp: 999(999) Str: 99(99) Arm: 99 Exp: 21/10000000
     * Hungry 0 5 1 5 2 5 3 5 4 5 5 5 6 5 7 5
     */
    String statString() {
        if (hpMax > MAX_HP) {
            hpCurrent -= hpMax - MAX_HP;
            hpMax = MAX_HP;
        }
        if (strMax > MAX_STRENGTH) {
            strCurrent -= strMax - MAX_STRENGTH;
            strMax = MAX_STRENGTH;
        }
        int armorclass = 0;
        if (armor != null) {
            if (armor.dEnchant > MAX_ARMOR) {
                armor.dEnchant = MAX_ARMOR;
            }
            armorclass = armor.getArmorClass();
        }
        if (expPoints > MAX_EXP) {
            expPoints = MAX_EXP;
        }
        if (exp > MAX_EXP_LEVEL) {
            exp = MAX_EXP_LEVEL;
        }
        
        return "Level: " + String.format("%2d", level.currentLevel) + " Gold: " + String.format("%6d", gold) + " Hp: " + String.format("%3d", hpCurrent) 
                + '(' + String.format("%3d", hpMax) + ") Str: " + String.format("%2d", strCurrent) + '(' + String.format("%2d", strMax, 2)
                + ") Arm: " + String.format("%2d", armorclass, 2) + " Exp: " + String.format("%2d", exp, 2) + '/' + String.format("%8d", expPoints, 8) + " " + hungerStr;
    }

    void printStat() {
        view.addch(level.nrow - 1, 0, statString());
        /// view.refresh();
    }

    void drop() {
        Toy obj;
        int ch;

        if (0 != (level.map[row][col] & (TOY | STAIRS | TRAP))) {
            tell("there's already something there");
            
            return;
        }
        if (null == pack) {
            tell("you have nothing to drop");
            
            return;
        }
        ch = packLetter("drop what?", Id.ALL_TOYS);
        if (ch == '\033') {
            return;
        }
        obj = (Toy) pack.getLetterToy(ch);
        if (obj == null) {
            tell("no such item.");
            
            return;
        }
        if (obj.kind == Id.SCARE_MONSTER) {
            obj.pickedUp = true;
        }
        obj.drop();
    }

//    private int is_pack_letter(int c) {
//        switch (c) {
//            case '?':
//                return Id.SCROLL;
//            case '!':
//                return Id.POTION;
//            case ':':
//                return Id.FOOD;
//            case ')':
//                return Id.WEAPON;
//            case ']':
//                return Id.ARMOR;
//            case '/':
//                return Id.WAND;
//            case '=':
//                return Id.RING;
//            case ',':
//                return Id.AMULET;
//            default:
//                break;
//        }
//        return 0;
//    }

    int packLetter(String prompt, int mask) {
        int ch;

        if (!pack.maskPack(mask)) {
            tell("nothing appropriate");

            return '\033';
        }
        tell(prompt);
        ch = self.rgetchar();
        while ((ch < 'a' || ch > 'z') && ch != '\033') {
            int m = mask;
            if (ch == '*' || m == 0) {
                m = Id.ALL_TOYS;
            }
            /// view.msg.check_message();
            ch = pack.inventory(m, view.msg, true);
            // System.out.println("In pack_letter " + ch);
        }
        view.msg.checkMessage();

        return ch;
    }

    void takeOff() {
        if (armor != null) {
            if (armor.isCursed) {
                tell(Toy.curseMessage);
            } else {
                level.moveAquatars(this);
                Toy obj = armor;
                unwear();
                tell("was wearing " + obj.getDesc());
                printStat();
                regMove();
            }
        } else {
            tell("not wearing any");
        }
    }

    void wear() {
        if (armor != null) {
            tell("your already wearing some");
            
            return;
        }
        int ch = packLetter("wear what?", Id.ARMOR);
        if (ch == '\033') {
            return;
        }
        Toy obj = (Toy) pack.getLetterToy(ch);
        if (null == obj) {
            tell("no such item.");
            
            return;
        }
        if (0 == (obj.kind & Id.ARMOR)) {
            tell("you can't wear that");
            
            return;
        }
        obj.identified = true;
        tell("wearing " + obj.getDesc());
        doWear(obj);
        printStat();
        regMove();
    }

    void wield() {
        if (weapon != null && weapon.isCursed) {
            tell(Toy.curseMessage);
            
            return;
        }
        int ch = packLetter("wield what?", Id.WEAPON);
        if (ch == '\033') {
            return;
        }
        Toy obj = (Toy) pack.getLetterToy(ch);
        if (obj == null) {
            if (ch == '-' && weapon != null) {
                unwield();
            } else {
                tell("No such item.");
            }
            
            return;
        }
        if (0 != (obj.kind & (Id.ARMOR | Id.RING))) {
            tell("you can't wield " + (0 != (obj.kind & Id.ARMOR) ? "armor" : "rings"));
            
            return;
        }
        if (0 != (obj.inUseFlags & Id.BEING_WIELDED)) {
            tell("in use");
        } else {
            unwield();
            tell("wielding " + obj.getDesc());
            doWield(obj);
            regMove();
        }
    }

    Toy find(int mask, String prompt, String fail) {
        int ch = packLetter(prompt, mask);
        if (ch == '\033') {
            return null;
        }
        view.msg.checkMessage();
        Toy t = (Toy) pack.getLetterToy(ch);
        if (t == null) {
            tell("no such item.");
            return null;
        }
        if (0 == (t.kind & mask)) {
            tell(fail);
            return null;
        }
        return t;
    }

    void callIt() {
        Toy obj = find(Id.SCROLL | Id.POTION | Id.WAND | Id.RING, "call what?", "surely you already know what that's called");
        if (obj == null) {
            return;
        }
        Id[] idTable = Id.getIdTable(obj);
        String buf = view.msg.getInputLine("call it:", "", idTable[obj.kind & 255].title, true, true);
        if (buf != null) {
            idTable[obj.kind & 255].idStatus = Id.CALLED;
            idTable[obj.kind & 255].title = buf;
        }
    }

    void kickIntoPack() {
        if (0 == (level.map[row][col] & TOY)) {
            tell("nothing here");
        } else {
            Toy obj = pickUp();
            if (obj != null && obj.ichar != 1) { // Not a dusted scroll
                regMove();
            }
        }
    }

    void monsterForWizard() {
        tell("type of monster? ");
        int ch = self.rgetchar();
        view.msg.checkMessage();
        if (ch == '\033' || ch < 'A' || ch > 'Z') {
            return;
        }
        Monster m = new Monster(level, ch - 'A');
        int r = row, c = col - 2;
        if (0 != (level.map[r][c] & (Level.FLOOR | Level.TUNNEL))) {
            m.putMonsterAt(row, col - 2);
        } else {
            tell("cannot put monster there!");
        }
    }

    void cToyForWizard() {
        if (pack.size() >= MAX_PACK_COUNT) {
            tell("pack full");
            
            return;
        }
        tell("type of object? ");
        int ch = self.rgetchar();
        view.msg.checkMessage();
        if (ch == '\033') {
            return;
        }
        Toy obj = level.wiztoy(this, ch);
        if (obj != null) {
            tell("Wizard got " + obj.getDesc());
            obj.addToPack(this);
        } else {
            tell("Wizard failed");
        }
    }

    Toy pickUp() {
        Toy obj = (Toy) level.levelToys.itemAt(row, col);
        if (obj == null) {
            tell("pick_up(): inconsistent", true);
            
            return null;
        }
        if (levitate > 0) {
            tell("you're floating in the air!");
            
            return null;
        }
        if (pack.size() >= MAX_PACK_COUNT && obj.kind != Id.GOLD) {
            tell("pack too full", true);
            
            return null;
        }
        level.map[row][col] &= ~TOY;

        // Pick up from a tunnel or door shows the substrate
        if (0 != (level.map[row][col] & TUNNEL)) {
            seen[row][col] |= wallcode('#');
        }
        if (0 != (level.map[row][col] & DOOR)) {
            seen[row][col] |= wallcode('+');
        }
        level.levelToys.remove(obj);

        if (obj.kind == Id.SCARE_MONSTER && obj.pickedUp) {
            tell("the scroll turns to dust as you pick it up");
            if (Id.idScrolls[Id.SCARE_MONSTER & 255].idStatus == Id.UNIDENTIFIED) {
                Id.idScrolls[Id.SCARE_MONSTER & 255].idStatus = Id.IDENTIFIED;
            }
            obj.ichar = 1; // Flag the dusted scroll

            return obj;
        }
        if (obj.kind == Id.GOLD) {
            gold += obj.quantity;
            tell(obj.getDesc(), true);
            printStat();
        } else {
            obj = obj.addToPack(this);
            if (obj != null) {
                obj.pickedUp = true;
                tell(obj.getDesc() + " (" + ((char) obj.ichar) + ")", true);
            }
        }
        return obj;
    }

    static final int[] LEVEL_POINTS = { 10, 20, 40, 80, 160, 320, 640, 1300, 2600, 5200, 10000, 20000, 40000, 80000, 160000, 320000, 1000000, 3333333, 6666666, MAX_EXP, 99900000 };

    int hpRaise() {
        return wizard ? 10 : self.rand.get(3, 10);
    }

    static int getExpLevel(int e) {
        int i;
        
        for (i = 0; i < LEVEL_POINTS.length; i++) {
            if (LEVEL_POINTS[i] > e) {
                break;
            }
        }
        
        return i + 1;
    }

    void add_exp(int e, boolean promotion) {
        expPoints += e;
        if (expPoints >= LEVEL_POINTS[exp - 1]) {
            int new_exp = getExpLevel(expPoints);
            if (expPoints > MAX_EXP) {
                expPoints = MAX_EXP + 1;
            }
            for (int i = exp + 1; i <= new_exp; i++) {
                tell("welcome to level " + i);
                if (promotion) {
                    int hp = hpRaise();
                    hpCurrent += hp;
                    hpMax += hp;
                }
                exp = i;
                printStat();
            }
        } else {
            printStat();
        }
    }

    void eat() {
        Toy obj = find(Id.FOOD, "eat what?", "you can't eat that");
        if (obj != null) {
            obj.eatenby();
            regMove();
        }
    }

    void quaff() {
        Potion obj = (Potion) find(Id.POTION, "quaff what?", "you can't drink that");
        if (obj != null) {
            obj.quaffby();
            regMove();
        }
    }

    void readScroll() {
        Scroll obj = (Scroll) find(Id.SCROLL, "read what?", "you can't read that");
        if (obj != null) {
            obj.readby();
            if (obj.kind != Id.SLEEP) {
                regMove();
            }
        }
    }

    void putOnRing() {
        if (leftRing != null && rightRing != null) {
            tell("wearing two rings already");
            
            return;
        }
        Toy obj = (Toy) find(Id.RING, "put on what?", "that's not a ring");
        if (obj == leftRing || obj == rightRing) {
            tell("that ring is already being worn");

            return;
        }
        if (obj != null) {
            int ch = 'r';
            if (leftRing == null) {
                ch = 'l';
                if (rightRing == null) {
                    ch = view.msg.leftOrRight();
                    if (ch == 0) {
                        view.msg.checkMessage();
                        return;
                    }
                }
            }
            if (ch == 'l') {
                obj.inUseFlags |= Id.ON_LEFT_HAND;
                leftRing = obj;
            } else {
                obj.inUseFlags |= Id.ON_RIGHT_HAND;
                rightRing = obj;
            }
            ringStats(true);
            view.msg.checkMessage();
            tell(obj.getDesc());
            regMove();
        }
    }

    void removeRing() {
        Toy obj = rightRing;
        if (leftRing != null && rightRing != null) {
            int ch = view.msg.leftOrRight();
            if (ch == 0) {
                view.msg.checkMessage();
                return;
            }
            if (ch == 'l') {
                obj = leftRing;
            }
        } else if (leftRing != null) {
            obj = leftRing;
        } else if (rightRing == null) {
            tell("there's no ring on that hand");
            return;
        }
        if (obj.isCursed) {
            tell(Toy.curseMessage);
        } else {
            obj.unPutOn();
            tell("removed " + obj.getDesc());
        }
    }

    void uncurseAll() {
        int i = pack.size();
        while (--i >= 0) {
            Toy obj = (Toy) pack.get(i);
            obj.isCursed = false;
        }
    }

    void tele() {
        level.putPlayer(this);
        vizset();
        moveSeen();
        beingHeld = false;
        bearTrap = 0;
    }

    void ringStats(boolean pr) {
        stealthy = 0;
        rRings = 0;
        eRings = 0;
        ringExp = 0;
        rTeleport = false;
        sustainStrength = true;
        addStrength = 0;
        regeneration = 0;
        rSeeInvisible = false;
        maintainArmor = false;
        autoSearch = 0;

        for (int i = 0; i < 2; i++) {
            Toy ring = null;
            if (i == 0 && leftRing != null) {
                ring = leftRing;
            } else if (i == 1 && rightRing != null) {
                ring = rightRing;
            } else {
                continue;
            }
            rRings++;
            eRings++;
            switch (ring.kind) {
                case Id.STEALTH:
                    stealthy++;
                    break;
                case Id.R_TELEPORT:
                    rTeleport = true;
                    break;
                case Id.REGENERATION:
                    regeneration++;
                    break;
                case Id.SLOW_DIGEST:
                    eRings -= 2;
                    break;
                case Id.ADD_STRENGTH:
                    addStrength += ring.klass;
                    break;
                case Id.SUSTAIN_STRENGTH:
                    sustainStrength = true;
                    break;
                case Id.DEXTERITY:
                    ringExp += ring.klass;
                    break;
                case Id.ADORNMENT:
                    break;
                case Id.R_SEE_INVISIBLE:
                    rSeeInvisible = true;
                    break;
                case Id.MAINTAIN_ARMOR:
                    maintainArmor = true;
                    break;
                case Id.SEARCHING:
                    autoSearch += 2;
                    break;
            }
        }
        if (pr) {
            printStat();
            view.markall(); // relight
        }
    }

    static final int[] HEALTAB = { 2, 20, 18, 17, 14, 13, 10, 9, 8, 7, 4, 3, 2 };
    int cHeal = 0;
    boolean bHealAlt = false;

    void heal() {
        if (hpCurrent == hpMax) {
            cHeal = 0;
            return;
        }
        int n = exp < HEALTAB.length ? HEALTAB[exp] : 2;
        if (++cHeal >= n) {
            hpCurrent++;
            if (bHealAlt = !bHealAlt) {
                hpCurrent++;
            }
            cHeal = 0;
            hpCurrent += regeneration;
            if (hpCurrent > hpMax) {
                hpCurrent = hpMax;
            }
            printStat();
        }
    }

    void zapp() {
        int d = view.msg.kbd_direction();
        if (d < 0) {
            return;
        }
        Toy wand = find(Id.WAND, "zap with what?", "you can't zap with that");
        if (wand == null) {
            return;
        }
        if (wand.klass <= 0) {
            tell("nothing happens");
        } else {
            wand.klass--;
            if ((wand.kind == Id.COLD) || (wand.kind == Id.FIRE)) {
                level.bounce(wand, d, row, col, 0);
                view.markall(); // relight
            } else {
                Monster monster = level.getZappedMonster(d, row, col);
                if (monster != null) {
                    if (wand.kind == Id.DRAIN_LIFE) {
                        level.wdrainLife(this, monster);
                    } else if (monster != null) {
                        monster.wakeUp();
                        monster.sConMon(this);
                        monster.zap_monster(this, wand.kind);
                        view.markall(); // relight
                    }
                }
            }
        }
        regMove();
    }

    void idCom() {
        view.msg.checkMessage();
        tell("Character you want help for(* for all):");
        int ch = self.mdGetchar();
        view.msg.checkMessage();
        Identifychar.cmdsList((char) ch, view.msg);
        view.markall(); // relight
    }

    boolean hasAmulet() {
        return pack.maskPack(Id.AMULET);
    }

    void playerInit() {
        pack = new ItemList(MAX_PACK_COUNT);
        level.getFood(true).addToPack(this);
        level.getFood(true).addToPack(this);
        level.getFood(true).addToPack(this);

        Toy obj = level.grArmor();
        obj.kind = Id.RINGMAIL;
        obj.klass = (Id.RINGMAIL & 255) + 2;
        obj.isProtected = false;
        obj.dEnchant = 1;
        obj.identified = true;
        obj.addToPack(this);
        doWear(obj);

        obj = level.grWeapon(Id.MACE);
        obj.hitEnchant = obj.dEnchant = 1;
        obj.identified = true;
        obj.addToPack(this);
        obj.isCursed = false;
        doWield(obj);

        obj = level.grWeapon(Id.BOW);
        obj.damage = "1d2";
        obj.hitEnchant = 1;
        obj.dEnchant = 0;
        obj.identified = true;
        obj.isCursed = false;
        obj.addToPack(this);

        obj = level.grWeapon(Id.ARROW);
        obj.quantity = self.rand.get(25, 35);
        obj.hitEnchant = 0;
        obj.dEnchant = 0;
        obj.identified = true;
        obj.isCursed = false;
        obj.addToPack(this);

        for (int i = 1; i < 10; i++) {
            obj = level.grScroll();
            obj.hitEnchant = 0;
            obj.dEnchant = 0;
            obj.identified = false;
            obj.isCursed = false;
            obj.addToPack(this);
        }
    }

    void idTrap() {
        view.msg.checkMessage();
        tell("direction? ");
        int d = Id.isDirection(self.rgetchar());
        view.msg.checkMessage();
        if (d < 0) {
            return;
        }
        Rowcol pt = level.getDirRc(d, row, col, false);
        int r = pt.row;
        int c = pt.col;
        Trap t;
        if (0 != (level.map[r][c] & TRAP) && 0 == (level.map[r][c] & HIDDEN) && (t = (Trap) level.levelTraps.itemAt(r, c)) != null) {
            tell(Trap.name[t.kind]);
        } else {
            tell("no trap there");
        }
    }

    void throwMissile() {
        int dir = view.msg.kbd_direction();
        if (dir < 0) {
            return;
        }
        int wch = packLetter("throw what?", Id.WEAPON);
        if (wch == '\033') {
            return;
        }
        view.msg.checkMessage();
        Toy missile = (Toy) pack.getLetterToy(wch);
        if (missile == null) {
            tell("no such item.");
            
            return;
        }
        if (0 != (missile.inUseFlags & Id.BEING_USED) && missile.isCursed) {
            tell(Toy.curseMessage);
            
            return;
        }
        missile.owner = this;
        missile.thrownby(dir);
        regMove();
    }

    boolean rogueIsAround(int r, int c) {
        r -= row;
        c -= col;

        return r >= -1 && r <= 1 && c >= -1 && c <= 1;
    }

    void rogueHit(Monster monster, boolean forceHit) {
        if (monster.check_imitator()) {
            if (blind == 0) {
                view.msg.checkMessage();
                tell("wait, that's a " + monster.name() + '!');
            }

            return;
        }
        int hitChance = 100;
        if (!forceHit) {
            hitChance = getHitChance(weapon);
        }
        if (wizard) {
            hitChance *= 2;
        }
        if (!self.rand.percent(hitChance)) {
            if (null == ihate) {
                hitMessage += who("miss", "misses") + " ";
            }
        } else {
            int dmg = getWeaponDamage(weapon);
            if (wizard) {
                dmg *= 3;
            }
            if (conMon) {
                monster.sConMon(this);
            }
            if (monster.damage(this, dmg, 0)) { /* still alive? */
                if (null == ihate) {
                    hitMessage += who("hit") + " ";
                }
            }
        }
        monster.checkGoldSeeker();
        monster.wakeUp();
    }

    boolean damage(Persona monster, int d, int other) {
        if (d >= hpCurrent) {
            hpCurrent = 0;
            printStat();
            killedBy(monster, other);
            
            return true;
        }
        if (d > 0) {
            self.flashadd(row, col, U_RED);
            hpCurrent -= d;
            printStat();
            if (hpCurrent <= hpMax / 8) {
                monster.gloat(this);
            }
        }
        
        return false;
    }

    void fight(boolean toTheDeath) {
        int ch;
        if (-2 == Id.isDirection(ch = self.rgetchar())) {
            tell("direction? ");
            ch = self.rgetchar();
        }
        view.msg.checkMessage();
        if (ch == '\033') {
            return;
        }
        int d = Id.isDirection(ch);
        Rowcol pt = level.getDirRc(d, row, col, false);

        int c = view.charat(pt.row, pt.col);
        if (c < 'A' || c > 'Z' || !level.canMove(row, col, pt.row, pt.col)) {
            tell("I see no monster there");
            
            return;
        }
        ihate = (Monster) level.levelMonsters.itemAt(pt.row, pt.col);
        if (null == ihate) {
            return;
        }

        int possibleDamage; // Fight should really be more symmetrical
        if (0 == (ihate.mFlags & Monster.STATIONARY)) {
            possibleDamage = Id.getDamage(ihate.mt.mDamage, null) * 2 / 3;
        } else {
            possibleDamage = ((Monster) ihate).stationaryDamage - 1;
        }

        while (null != ihate) {
            oneMoveRogue(ch, false);
            if ((!toTheDeath && hpCurrent <= possibleDamage) || level.self.interrupted || 0 == (level.map[pt.row][pt.col] & MONSTER)) {
                ihate = null;
            } else {
                Monster monster = (Monster) level.levelMonsters.itemAt(pt.row, pt.col);
                if (monster != ihate)
                    ihate = null;
            }
        }
    }

    void rust(Monster monster) {
        if (null == armor || armor.getArmorClass() <= 1 || armor.kind == Id.LEATHER) {
            return;
        }
        if (armor.isProtected || maintainArmor) {
            if (monster != null && 0 == (monster.mFlags & Monster.RUST_VANISHED)) {
                tell("the rust vanishes instantly");
                monster.mFlags |= Monster.RUST_VANISHED;
            }
        } else {
            armor.dEnchant--;
            tell("your armor weakens");
            printStat();
        }
    }

    void freeze(Monster monster) {
        int freezePercent = 99;
        int i, n;

        if (self.rand.percent(12)) {
            return;
        }
        freezePercent -= strCurrent + strCurrent / 2;
        freezePercent -= (exp + ringExp) * 4;
        if (armor != null) {
            freezePercent -= armor.getArmorClass() * 5;
        }
        freezePercent -= hpMax / 3;

        if (freezePercent > 10) {
            monster.mFlags |= Monster.FREEZING_ROGUE;
            tell("you are frozen", true);

            n = self.rand.get(4, 8);
            for (i = 0; i < n; i++) {
                level.moveMonsters(this);
            }
            if (self.rand.percent(freezePercent)) {
                for (i = 0; i < 50; i++) {
                    level.moveMonsters(this);
                }
                killedBy(null, Monster.HYPOTHERMIA);
            } else {
                tell("you_can_move_again", true);
            }
            monster.mFlags &= ~Monster.FREEZING_ROGUE;
        }
    }

    void sting(Monster monster) {
        int stingChance = 35 + 36;
        if (strCurrent <= 3 || sustainStrength) {
            return;
        }
        if (armor != null) {
            stingChance = 35 + 6 * (6 - armor.getArmorClass());
        }

        if (exp + ringExp > 8) {
            stingChance -= 6 * (exp + ringExp - 8);
        }
        if (self.rand.percent(stingChance)) {
            tell("the " + monster.name() + "'s bite has weakened you");
            strCurrent--;
            printStat();
        }
    }

    void dropLevel() {
        if (self.rand.percent(80) || exp <= 5) {
            return;
        }
        expPoints = LEVEL_POINTS[exp - 2] - self.rand.get(9, 29);
        exp -= 2;
        int hp = hpRaise();
        hpCurrent -= hp;
        if (hpCurrent <= 0) {
            hpCurrent = 1;
        }
        hpMax -= hp;
        if (hpMax <= 0) {
            hpMax = 1;
        }
        add_exp(1, false);
    }

    void drainLife() {
        if (self.rand.percent(60) || hpMax <= 30 || hpCurrent < 10) {
            return;
        }
        int n = self.rand.get(1, 3); /* 1 Hp, 2 Str, 3 both */

        if (n != 2 || !sustainStrength) {
            tell("you feel weaker");
        }
        if (n != 2) {
            hpMax--;
            hpCurrent--;
            lessHp++;
        }
        if (n != 1) {
            if (strCurrent > 3 && !sustainStrength) {
                strCurrent--;
                if (self.rand.coin()) {
                    strMax--;
                }
            }
        }
        printStat();
    }

    void win() {
        Date d = new Date();
        self.starttime = d.getTime() - self.starttime;
        tell("YOU WON!");
        col = -1;
        trapDoor = true;
        gameOver = true;
        view.empty();
        view.msg.banner(1, 6, option.nickName);
        view.addch(10, 11, "@   @  @@@   @   @      @  @  @   @@@   @   @   @");
        view.addch(11, 11, " @ @  @   @  @   @      @  @  @  @   @  @@  @   @");
        view.addch(12, 11, "  @   @   @  @   @      @  @  @  @   @  @ @ @   @");
        view.addch(13, 11, "  @   @   @  @   @      @  @  @  @   @  @  @@");
        view.addch(14, 11, "  @    @@@    @@@        @@ @@    @@@   @   @   @");
        view.addch(17, 11, "Congratulations  you have  been admitted  to  the");
        view.addch(18, 11, "Fighters' Guild.   You return home,  sell all your");
        view.addch(19, 11, "treasures at great profit and retire into comfort.");
        view.addch(21, 16, "You have " + gold + " in gold");
        view.addch(23, 11, "Press SPACE to see the hall of fame");
        view.refresh();
        self.waitForAck();
    }

    String obituaryString(Persona monster, int other) {
        String obit = "";
        if (other != 0) {
            switch (other) {
                case Monster.HYPOTHERMIA:
                    obit = "Died of hypothermia";
                    break;
                case Monster.STARVATION:
                    obit = "Died of starvation";
                    break;
                case Monster.POISON_DART:
                    obit = "Killed by a dart";
                    break;
                case Monster.QUIT:
                    obit = "Quit the game";
                    break;
                case Monster.KFIRE:
                    obit = "Killed by fire";
                    break;
            }
        } else if (monster != null) {
            /* Took out the vowel lookup */
            // char i= monster.name().charAt(0);
            // if(i=='a'||i=='e'||i=='i'||i=='o'||i=='u')
            if (Id.isVowel((int) monster.name().charAt(0))) {
                obit = "Killed by an " + monster.name();
            } else {
                obit = "Killed by a " + monster.name();
            }
        }
        return obit;
    }

    void killedBy(Persona monster, int other) {
        Date d = new Date();
        self.starttime = d.getTime() - self.starttime;
        if (other != Monster.QUIT) {
            gold = ((gold * 9) / 10);
        }
        String obit = obituaryString(monster, other);
        gameOverMessage = obit;

        String s = obit + " with " + gold + " gold";
        col = -1;
        gameOver = true;
        trapDoor = true;
        if (!option.noSkull) {
            view.empty();
            view.addch(4, 32, "__---------__");
            view.addch(5, 30, "_~             ~_");
            view.addch(6, 29, "/                 \\");
            view.addch(7, 28, "~                   ~");
            view.addch(8, 27, "/                     \\");
            view.addch(9, 27, "|    XXXX     XXXX    |");
            view.addch(10, 27, "|    XXXX     XXXX    |");
            view.addch(11, 27, "|    XXX       XXX    |");
            view.addch(12, 28, "\\         @         /");
            view.addch(13, 29, "--\\     @@@     /--");
            view.addch(14, 30, "| |    @@@    | |");
            view.addch(15, 30, "| |           | |");
            view.addch(16, 30, "| vvVvvvvvvvVvv |");
            view.addch(17, 30, "|  ^^^^^^^^^^^  |");
            view.addch(18, 31, "\\_           _/");
            view.addch(19, 33, "~---------~");
            view.addch(21, 8, option.nickName);
            view.addch(22, 8, s);
            view.addch(23, 8, "Press SPACE to see the graveyard");
        } else {
            tell(s);
            tell("Press SPACE to see the graveyard");
        }
        view.refresh();
        self.waitForAck();
    }

    void initSeen() {
        // terrible, terrible hack
        // I couldn't figure out another way to prevent the level
        // for being initialized as "unseen" when it's loaded from
        // a saved file. This just avoids the first 2 times this
        // method is run after a game is loaded from a save - UGH!
        if (justLoaded > 0) {
            justLoaded--;

            return;
        }
        seen = level.initSeen();
        vizset();
        moveSeen();
    }

    int wallcode(char ch) {
        if (ch == '|') {
            return 0x10;
        }
        if (ch == '-') {
            return 0x20;
        }
        if (ch == '+') {
            return 0x30;
        }
        if (ch == '#') {
            return 0x40;
        }
        if (ch == '%') {
            return 0x50;
        }
        if (ch == '^') {
            return 0x60;
        }
        
        return 0;
    }

    void moveSeen() {
        for (int r = 0; r < level.nrow; r++) {
            for (int c = 0; c < level.ncol; c++) {
                int w = seen[r][c];
                int v = w & 15;
                w &= 0xf0;
                if (v >= 4) {
                    if (v < 12) {
                        if (v < 8) {
                            char ch = level.getChar(r, c);
                            w = wallcode(ch);
                        }
                        view.mark(r, c);
                    } else {
                        v -= 8;
                    }
                    if (v < 8) {
                        v += 4;
                    } else {
                        v -= 8;
                    }
                    seen[r][c] = (char) (v + w);
                }
            }
        }
    }

    void vizset() {
        if (0 != (level.map[row][col] & TUNNEL)) {
            for (int k = 0; k < 8; k++) {
                int r = row + Id.X_TABLE[k];
                int c = col + Id.Y_TABLE[k];
                int mask = level.map[r][c];
                if (0 != (mask & (TUNNEL | DOOR))) {
                    if (0 == (mask & HIDDEN)) {
                        seen[r][c] |= 4;
                    }
                }
            }
        } else {
            for (int r = 0; r < level.nrow; r++) {
                for (int c = 0; c < level.ncol; c++) {
                    if (seen[r][c] != 0) {
                        if (level.sees(r, c, row, col)) {
                            seen[r][c] |= 4;
                        }
                    }
                }
            }
        }
        seen[row][col] |= 4;
    }

    boolean canSee(int r, int c) {
        if (blind > 0) {
            return false;
        }

        return 0 != (seen[r][c] & 8);
    }

    String name() {
        return option.nickName;
    }

    void saveGame() {
        System.out.println("Saving game.");
        self.pullIds();
        try {
            File saveFile = new File(System.getProperty("user.home") + "/.rogue/rogue.ser");
            if (!saveFile.getParentFile().exists()) {
                saveFile.getParentFile().mkdirs();
            }
            FileOutputStream fileOut = new FileOutputStream(saveFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(self);
            out.close();
            fileOut.close();
            System.out.printf("Serialized data is saved in rogue.ser");
        } catch (IOException i) {
            i.printStackTrace();
        }
        gameOver = true;
        savedGame = true;
    }
}
