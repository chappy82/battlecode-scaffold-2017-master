package JustScouts;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import static battlecode.common.GameConstants.BULLET_TREE_RADIUS;
import static battlecode.common.GameConstants.GENERAL_SPAWN_OFFSET;


/**
 * Created by Max_Inspiron15 on 1/10/2017.
 */
public strictfp class RobotPlayer {
    static RobotController rc;
    static Random myRand;
    @SuppressWarnings("unused")
    // Keep broadcast channels
    static int GARDENER_CHANNEL = 5;
    static int LUMBERJACK_CHANNEL = 6;
    static int SOLDIER_CHANNEL = 7;
    static int SCOUT_CHANNEL = 8;
    static int ARCHON_CHANNEL = 9;

    static int MOVELOC_CHANNEL = 10;
    static int ENEMY_BASE_CHANNEL = 11;

    // Keep important numbers here
    static int GARDENER_MAX = 4;
    static int LUMBERJACK_MAX = 1;
    static int SCOUT_MAX = 3;
    static int MAX_TREES = 20;
    static int MAX_ANGLE = 360;
    static int START_MAKING_UNITS = 300;

    static int[] possibleDir = new int[]{0, 45, -45, 90, -90, 135, -135, 180};
    static ArrayList<MapLocation> pastLocations = new ArrayList<MapLocation>();

    static boolean patient = true;
    static boolean farmspot = false;
    static boolean is_alive = true;
    static Direction prevExploreDirection = null;
    static int turns_waited = 0;
    static Direction iShotDirection = null;

    static Direction SPAWN_DIRECTION = Direction.getEast().rotateLeftDegrees((float)59.8);

    public static void run(RobotController rc) throws GameActionException {
        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        myRand = new Random(rc.getID());

        //sets an archon to the leader
        if (rc.getType() == RobotType.ARCHON && rc.getRoundNum()==0) {
            rc.broadcast(0, rc.getID());

        }
        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case SCOUT:
                runScout();
        }
    }

    private static void runScout() throws GameActionException {
        while (true) {
            try {

                findBullets();
                //dodge();
                senseAndShootEnemiesScout();

                if (!rc.hasMoved()) {
                    goToMoveLoc();
                }
                Clock.yield();      //ends the turn
        } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void findBullets() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees();
        MapLocation treeLoc;
        int treeID;

        for (TreeInfo tree : trees) {
            if ((tree.getTeam() == Team.NEUTRAL && tree.getContainedBullets() > 0)) {
                treeLoc = tree.getLocation();
                treeID = tree.getID();
                if (rc.canShake(treeID)) {
                    rc.shake(treeID);
                } else {
                    rc.move(treeLoc);
                }
                break;
            }

        }
    }


    static void runArchon() throws GameActionException {
        while (true) {
            try {

                System.out.println(rc.getInitialArchonLocations(rc.getTeam()));


                //keeping track of its position

                wander();
                Direction dir = randomDirection();
                int prevNumGard = rc.readBroadcast(GARDENER_CHANNEL);

                if (rc.getRoundNum() == 1) {
                    initialBroadcast();
                }
                if (prevNumGard < GARDENER_MAX && (rc.getTreeCount() >= prevNumGard*3) && rc.canHireGardener(dir)) {
                    rc.hireGardener(dir);
                    rc.broadcast(GARDENER_CHANNEL, prevNumGard + 1);
                }

                if (rc.getRoundNum()>=(rc.getRoundLimit()-200) || rc.getTeamBullets() >= 10000) {
                    rc.donate(rc.getTeamBullets());
                }
                Clock.yield();      //ends the turn
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private static void initialBroadcast() throws GameActionException {
        int enemyLocation = translateMapToInt(rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);
        rc.broadcast(ENEMY_BASE_CHANNEL, enemyLocation);
        System.out.println("int loc = " + enemyLocation);
        System.out.println("map loc = " + rc.getInitialArchonLocations(rc.getTeam().opponent())[0]);


    }


    static void runGardener() throws GameActionException {
        while (true) {
            try {

                checkIfDead(GARDENER_CHANNEL);

                //An initial build order for the first few turns
                if (rc.getRoundNum() < 20) {
                    initialBuildOrder();
                } else {
                    tryToPlant();
                    tryToWater();
                    tryToSpawn();
                }

                Clock.yield();
                /*if (prevNumGard <= LUMBERJACK_MAX && rc.canBuildRobot(RobotType.LUMBERJACK, dir)) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                    rc.broadcast(LUMBERJACK_CHANNEL, prevNumGard + 1);
                }*/



                } catch(Exception e){
                    e.printStackTrace();
            }
        }
    }

    private static void checkIfDead(int channel) throws GameActionException {
        if (is_alive && rc.getHealth() <= 2) {
            int prevUnits = rc.readBroadcast(channel);
            rc.broadcast(channel, prevUnits - 1);
            is_alive = false;
        }
    }

    private static void initialBuildOrder() throws GameActionException {
        int prevNumScout = rc.readBroadcast(SCOUT_CHANNEL);
        if (rc.canBuildRobot(RobotType.SCOUT, randomDirection())) {
            rc.buildRobot(RobotType.SCOUT, randomDirection());
            System.out.println("MAKING SCOUT");
            rc.broadcast(SCOUT_CHANNEL, prevNumScout + 1);
        }
        goToMoveLoc();

    }

    private static void tryToSpawn() throws GameActionException {
        int prevNumScout = rc.readBroadcast(SCOUT_CHANNEL);
        int prevNumSoldier = rc.readBroadcast(SOLDIER_CHANNEL);
        int prevNumLumberjack = rc.readBroadcast(LUMBERJACK_CHANNEL);
        int roundnum = rc.getRoundNum();
        //for some reason I have to spawn at 359/6 degrees from East
        //for the first START_MAKING_UNITS rounds just build scouts?
        if (roundnum < START_MAKING_UNITS && prevNumScout < SCOUT_MAX && rc.canBuildRobot(RobotType.SCOUT, SPAWN_DIRECTION)) {
            rc.buildRobot(RobotType.SCOUT, SPAWN_DIRECTION);
            rc.broadcast(SCOUT_CHANNEL, prevNumScout + 1);

        } else if (roundnum > START_MAKING_UNITS && rc.canBuildRobot(RobotType.SOLDIER, SPAWN_DIRECTION)) {
            //after round START_MAKING_UNITS start spamming soldiers
            if (prevNumLumberjack < LUMBERJACK_MAX && rc.canBuildRobot(RobotType.LUMBERJACK, SPAWN_DIRECTION)) {
                rc.buildRobot(RobotType.LUMBERJACK, SPAWN_DIRECTION);
                rc.broadcast(LUMBERJACK_CHANNEL, prevNumLumberjack + 1);
            }
            rc.buildRobot(RobotType.SOLDIER, SPAWN_DIRECTION);
            rc.broadcast(SOLDIER_CHANNEL, prevNumSoldier + 1);
        }
    }




    private static void explore() throws GameActionException {
        Direction candidateDirection;
        MapLocation candidateLocation;

        if (prevExploreDirection == null) {
            prevExploreDirection = dirToEnemyBase();
        } else if (!rc.canMove(prevExploreDirection)) {
            for (int i=0; i<4; i++) {
                if (rc.canMove(prevExploreDirection = prevExploreDirection.rotateRightDegrees(i*90))) {
                    rc.move(prevExploreDirection);
                }
            }
        } else {
            rc.move(prevExploreDirection);
        }
    }

    private static void moveDirection(Direction ahead) throws GameActionException {

        Direction candidateDirection = ahead;
        MapLocation candidateLocation = rc.getLocation().add(candidateDirection);

        for (int i:possibleDir) {

            if (patient) {
                if (rc.canMove(candidateDirection = ahead.rotateRightDegrees(i))
                        && !pastLocations.contains(candidateLocation)) {
                    pastLocations.add(rc.getLocation());
                    //remember the past locations
                    if (pastLocations.size()>20) {
                        pastLocations.remove(0);
                    }
                    rc.move(candidateDirection);
                    return;
                } else if (rc.getType() == RobotType.LUMBERJACK && rc.senseTreeAtLocation(candidateLocation)!= null
                        && rc.senseTreeAtLocation(candidateLocation).getTeam() == Team.NEUTRAL) {
                    rc.chop(candidateLocation);
                }

            } else {
                if (rc.canMove(candidateDirection)) {
                    rc.move(candidateDirection);
                    return;
                    //If it can't move, attacks any tree in its way.
                }
            }
        }
        pastLocations.remove(0);


    }

    public static void tryToPlant() throws GameActionException{

        MapLocation loc = rc.getLocation();
        loc.add(Direction.getEast());
        //size of a farm = diameter of gardener + diameter of 2 trees
        float farmSize = RobotType.GARDENER.bodyRadius*2 + 2*(BULLET_TREE_RADIUS*2 + GENERAL_SPAWN_OFFSET);
        boolean nearbyGardener = false;
        float pathSize = 2;
        float gardenerSpacing = RobotType.GARDENER.bodyRadius*2 + BULLET_TREE_RADIUS*2 + pathSize;


        //check if nearby a gardener
        for (RobotInfo bot: rc.senseNearbyRobots(gardenerSpacing, rc.getTeam())) {
            if (bot.getType() == RobotType.GARDENER) {
                nearbyGardener = true;
                break;
            }
        }

        // Checks if:
        // is far enough away from another gardener
        // the gardener is currently in a farm
        // has enough room to build a complete farm
        if (farmspot || (!nearbyGardener && !rc.isCircleOccupiedExceptByThisRobot(rc.getLocation(), farmSize))) {
            if (!farmspot) {
                System.out.println("I have started a farm!");
            }
            plant();
            farmspot = true;
        //If the gardener waits too long, get's impatient and plants in a worse spot
        } else if (turns_waited >= 20 && !nearbyGardener) {
            plant();
            farmspot = true;
            //ANOTHER OPTION: else if (turns_waited >= 20 && !rc.isCircleOccupiedExceptByThisRobot(rc.getLocation(), 3))
        }
        //else look for a for new
        else {
            explore();
            turns_waited ++;
        }
        return;
    }

    //plants trees in every spot around a gardener except for North East
    private static void plant() throws GameActionException {
        int numTrees = rc.getTreeCount();
        Direction dir = Direction.getEast();
        Direction treeDir;

        //builds a tree if there's 5 or less around it.
        if (numTrees < MAX_TREES && rc.hasTreeBuildRequirements() && numTrees<MAX_TREES) {
            for(int i=0; i<5; i++) {
                if (rc.canPlantTree((treeDir = dir.rotateRightDegrees(60*i)))) {
                    rc.plantTree(treeDir);
                    //System.out.println("I am at: " + rc.getLocation());
                }
            }
        }

    }

    //tries to water any trees that are nearby
    public static void tryToWater() throws GameActionException{
        if(rc.canWater()) {
            TreeInfo[] nearbyTrees = rc.senseNearbyTrees();
            for (int i = 0; i < nearbyTrees.length; i++)
                if(nearbyTrees[i].getHealth()<GameConstants.BULLET_TREE_MAX_HEALTH-GameConstants.WATER_HEALTH_REGEN_RATE) {
                    if (rc.canWater(nearbyTrees[i].getID())) {
                        rc.water(nearbyTrees[i].getID());
                        break;
                    }
                }
        }
    }

    static void runSoldier() throws GameActionException {
        while (true) {
            try {
                checkIfDead(SOLDIER_CHANNEL);
                senseAndShootEnemies();

                goToMoveLoc();
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //kill gardener by lurking next to it
    private static void scoutKillGardener(RobotInfo gardener) throws GameActionException {
        MapLocation gardenerLoc = gardener.getLocation();
        //could hide in trees?
        //TreeInfo[] nearbyTree = rc.senseNearbyTrees(gardenerLoc,2, gardener.getTeam()); //101 bytes

        if (rc.getLocation().distanceTo(gardenerLoc) < 3 && rc.canFireSingleShot()) {
            shootSingleShotAtBot(gardener);

            Clock.yield();
        } else {
            moveTo(gardenerLoc.subtract(1));
            Clock.yield();
        }

    }

    //shoots a single shot a Map Location enemyLoc
    private static void shootSingleShotAtBot(RobotInfo enemyBot) throws GameActionException {
        MapLocation enemyLoc = enemyBot.getLocation();

        //should really check that there isn't a friendly mate in the way

        if (rc.canFireSingleShot()) {
            rc.fireSingleShot(rc.getLocation().directionTo(enemyLoc));
        }
    }


    //moves to a maplocation w/ better pathing than rc.move
    private static void moveTo(MapLocation loc) throws GameActionException {
        //if you can move to the location, mvoe there
        if (rc.canMove(loc)) {
            rc.move(loc);
        } else {
            moveDirection(rc.getLocation().directionTo(loc));
        }
    }


    private static void senseAndShootEnemiesScout() throws GameActionException {
        if (!rc.hasMoved()) {
            RobotInfo[] bots = rc.senseNearbyRobots();  //100 bytes
            for (RobotInfo b : bots) {
                if ((b.getTeam() != rc.getTeam())) {
                    //keepdistanceScout(b);
                    System.out.println("I have used " + Clock.getBytecodeNum() + "bytes");

                    //check that it won't hit my team

                    //if I want to shoot everyone I come across
                    //pretty useless now that scouts do 0 damage
                    /*if (rc.canFireSingleShot() && b.getType() != RobotType.ARCHON) {
                        Direction shootDirection = rc.getLocation().directionTo(b.location);
                        rc.fireSingleShot(shootDirection);
                    }*/

                    if (b.getType() == RobotType.GARDENER) {
                        //try and kill the enemy gardener
                        scoutKillGardener(b);
                        rc.broadcast(MOVELOC_CHANNEL, translateMapToInt(b.getLocation()));
                    } else if (b.getType() == RobotType.ARCHON) {
                        //broadcast where the enemy archon is:
                        rc.broadcast(ENEMY_BASE_CHANNEL, translateMapToInt(b.getLocation()));
                    }
                    break;
                }

            }
        }
    }



    private static void senseAndShootEnemies() throws GameActionException {
        if (!rc.hasMoved()) {
            RobotInfo[] bots = rc.senseNearbyRobots();
            for (RobotInfo b : bots) {
                if ((b.getTeam() != rc.getTeam())) {


                    //check that it won't hit my team
                    //probably add method willHitTeam?

                    //broadcast the enemy location if there currently isn't one
                    if (rc.readBroadcast(MOVELOC_CHANNEL) == 0) {
                        rc.broadcast(MOVELOC_CHANNEL, translateMapToInt(b.getLocation()));
                    }

                    if (b.getType() == RobotType.GARDENER) {
                        //try and kill the enemy gardener
                        rc.broadcast(MOVELOC_CHANNEL, translateMapToInt(b.getLocation()));
                        goToMoveLoc();
                        shootSingleShotAtBot(b);
                    } else if (b.getType() == RobotType.LUMBERJACK) {
                        //keep distance from the enemy lumberjack while shooting at it
                        keepDistance(b);
                        shootSingleShotAtBot(b);
                    } else if (b.getType() == RobotType.ARCHON) {
                        //broadcast where the enemy archon is:
                        rc.broadcast(ENEMY_BASE_CHANNEL, translateMapToInt(b.getLocation()));
                        goToMoveLoc();
                        shootSingleShotAtBot(b);
                    } else {
                        dodge();
                        shootSingleShotAtBot(b);
                    }
                    break;
                }

            }
        }
    }



    //idea is to make a code that means that a unit will space far enough from an enemy

    /*private static void keepdistanceScout(RobotInfo bot) throws GameActionException {
        MapLocation enemyLoc = bot.getLocation();
        MapLocation myLoc = rc.getLocation();
        RobotType type = bot.getType();
        float size = RobotType.GARDENER.bodyRadius + RobotType.SCOUT.bodyRadius+2;
        float dist = myLoc.distanceTo(enemyLoc);
        if (type == RobotType.GARDENER) {
            if (dist >= size && rc.canMove(enemyLoc)) {
                rc.move(enemyLoc.subtract(myLoc.directionTo(enemyLoc)));
            } else {
                if (rc.canFireSingleShot()) {
                    rc.move(myLoc.directionTo(enemyLoc),dist-size);
                    rc.fireSingleShot(rc.getLocation().directionTo(enemyLoc));
                    System.out.println("I'M CLOSE TO YOU");
                    Clock.yield();
                }
            }
        } else if(myLoc.distanceTo(enemyLoc) < 5 && bot.getType() != RobotType.ARCHON) {
            //run away if the enemy is too close unless it's an archon, then ignore
            moveDirection(myLoc.directionTo(enemyLoc).rotateLeftDegrees(90));
        }
    }*/

    //kite away from lumberjacks
    private static void keepDistance(RobotInfo bot) throws GameActionException {
        MapLocation enemyLoc = bot.getLocation();
        MapLocation myLoc = rc.getLocation();
        RobotType type = bot.getType();

        if (myLoc.distanceTo(enemyLoc) < 7 && bot.getType() == RobotType.LUMBERJACK) {
            //run away if the enemy is too close
            moveDirection(myLoc.directionTo(enemyLoc).opposite());
        }
    }

    static void runLumberjack() throws GameActionException {
        while (true) {
            try {
                checkIfDead(LUMBERJACK_CHANNEL);
                RobotInfo[] bots = rc.senseNearbyRobots();
                for (RobotInfo b : bots) {
                    if ((b.getTeam() != rc.getTeam()) && rc.canStrike()) {
                        rc.strike();
                        Direction chase = rc.getLocation().directionTo(b.getLocation());
                        if (rc.canMove(chase)) {
                            rc.move(chase);
                        }
                        break;
                    }
                }
                /*TreeInfo[] trees = rc.senseNearbyTrees();
                for (TreeInfo t : trees) {
                    if (rc.canChop(t.getLocation())) {
                        rc.chop(t.getLocation());
                        break;
                    }
                }*/
                if (! rc.hasAttacked()) {
                    goToMoveLoc();
                }
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void goToMoveLoc() throws GameActionException {

        if (!rc.hasMoved()) {

            if (rc.readBroadcast(MOVELOC_CHANNEL) == 0) {
                if (rc.getType() == RobotType.SCOUT) {
                    explore();
                } else {
                    goToEnemyBase();
                }
            } else {
                MapLocation map = translateIntToMap(rc.readBroadcast(MOVELOC_CHANNEL));
                if (rc.getLocation().distanceTo(map) < 1) {
                    rc.broadcast(MOVELOC_CHANNEL, 0);
                }
                moveDirection(rc.getLocation().directionTo(map));
                System.out.println("Going to enemy location at: " + map);
            }
        }


    }


    //goes to the location saved on the ENEMY_BASE_CHANNEL
    private static void goToEnemyBase() throws GameActionException {

        if (rc.readBroadcast(ENEMY_BASE_CHANNEL) == 0) {
            wander();
        } else {
            MapLocation map = translateIntToMap(rc.readBroadcast(ENEMY_BASE_CHANNEL));
            if (rc.getLocation().distanceTo(map) < 1) {
                //if it gets there, which will basically only ever happen if everything around it is dead, set the
                //to 0 -> no location
                rc.broadcast(ENEMY_BASE_CHANNEL, 0);
            }
            moveDirection(rc.getLocation().directionTo(map));
            System.out.println("Going to enemy base at: " + map);
        }


    }

    private static Direction dirToEnemyBase() throws GameActionException {
        MapLocation map = translateIntToMap(rc.readBroadcast(ENEMY_BASE_CHANNEL));
        return rc.getLocation().directionTo(map);
    }


    public static void wander() throws GameActionException {
        try {
            Direction dir = randomDirection();
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static MapLocation translateIntToMap(int valueInt) {
        float xValue = valueInt % 1000;
        float yValue = (int)(valueInt/1000);
        MapLocation valueMap = new MapLocation(xValue, yValue);


        return valueMap;
    }

    //creates a 6 digit int of the form yyyxxx
    public static int translateMapToInt(MapLocation valueMap) {
        float xvalue = valueMap.x;
        float yvalue = valueMap.y;

        int valueInt = (int)xvalue;
        valueInt += ((int)yvalue*1000);

        return valueInt;
    }


    public static Direction randomDirection() {
        return(new Direction(myRand.nextFloat()*2*(float)Math.PI));
    }

    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }


    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (!rc.hasMoved() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        //boolean moved = rc.hasMoved();
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(!rc.hasMoved() && rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(! rc.hasMoved() && rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    static boolean trySidestep(BulletInfo bullet) throws GameActionException{

        Direction towards = bullet.getDir();
        MapLocation leftGoal = rc.getLocation().add(towards.rotateLeftDegrees(90), rc.getType().bodyRadius);
        MapLocation rightGoal = rc.getLocation().add(towards.rotateRightDegrees(90), rc.getType().bodyRadius);

        return(tryMove(towards.rotateRightDegrees(90)) || tryMove(towards.rotateLeftDegrees(90)));
    }

    static void dodge() throws GameActionException {
        BulletInfo[] bullets = rc.senseNearbyBullets();
        for (BulletInfo bi : bullets) {
            if (willCollideWithMe(bi)) {
                trySidestep(bi);
            }
        }

    }
}
