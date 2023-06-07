package me.fiveave.untenshi;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static me.fiveave.untenshi.main.maxspeed;

class untenshi {

    private final Player p;
    private int mascon;
    private int speedlimit;
    private int signallimit;
    private int points;
    private int atsforced;
    private int lastsisp;
    private int lastspsp;
    private int dooropen;
    private int[] stopoutput;
    private int[] atodest;
    private int atostoptime;
    private double current;
    private double speed;
    private double atospeed;
    private double[] stoppos;
    private Location lastsisign;
    private Location lastspsign;
    private Location[] resettablesisign;
    private Location[] ilposlist;
    private Location[] ilposoccupied;
    private String signaltype;
    private String signalorderptn;
    private boolean playing;
    private boolean freemode;
    private boolean reqstopping;
    private boolean overrun;
    private boolean fixstoppos;
    private boolean staaccel;
    private boolean staeb;
    private boolean atsbraking;
    private boolean atsping;
    private boolean atspnear;
    private boolean doordiropen;
    private boolean doorconfirm;
    private boolean frozen;
    private boolean allowatousage;
    private boolean atopisdirect;
    private boolean atoforcebrake;
    private MinecartGroup train;
    private ItemStack[] inv;
    private long ilenterqueuetime;

    untenshi(Player p, Boolean freemode, Boolean allowatousage) {
        this.p = p;
        this.setPlaying(false);
        this.setSpeed(0.0);
        this.setSignallimit(maxspeed);
        this.setSpeedlimit(maxspeed);
        this.setFrozen(false);
        this.setAllowatousage(allowatousage);
        this.setFreemode(freemode);
        this.setDooropen(0);
        this.setDoordiropen(false);
        this.setDoorconfirm(false);
        this.setFixstoppos(false);
        this.setStaeb(false);
        this.setStaaccel(false);
        this.setMascon(-9);
        this.setCurrent(-480.0);
        this.setPoints(30);
        this.setAtsbraking(false);
        this.setAtsping(false);
        this.setAtspnear(false);
        this.setOverrun(false);
        this.setSignaltype("ats");
        this.setSignalorderptn("default");
        this.setReqstopping(false);
        this.setAtsforced(0);
        this.setAtopisdirect(false);
        this.setAtoforcebrake(false);
        this.setTrain(null);
        this.setStoppos(null);
        this.setAtospeed(-1);
        this.setAtodest(null);
        this.setAtostoptime(-1);
        this.setLastsisign(null);
        this.setLastspsign(null);
        this.setLastsisp(-1);
        this.setLastspsp(-1);
    }

    public int getMascon() {
        return mascon;
    }

    public void setMascon(int mascon) {
        this.mascon = mascon;
    }

    public int getSpeedlimit() {
        return speedlimit;
    }

    public void setSpeedlimit(int speedlimit) {
        this.speedlimit = speedlimit;
    }

    public int getSignallimit() {
        return signallimit;
    }

    public void setSignallimit(int signallimit) {
        this.signallimit = signallimit;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getAtsforced() {
        return atsforced;
    }

    public void setAtsforced(int atsforced) {
        this.atsforced = atsforced;
    }

    public int getLastsisp() {
        return lastsisp;
    }

    public void setLastsisp(int lastsisp) {
        this.lastsisp = lastsisp;
    }

    public int getLastspsp() {
        return lastspsp;
    }

    public void setLastspsp(int lastspsp) {
        this.lastspsp = lastspsp;
    }

    public int getDooropen() {
        return dooropen;
    }

    public void setDooropen(int dooropen) {
        this.dooropen = dooropen;
    }

    public int[] getStopoutput() {
        return stopoutput;
    }

    public void setStopoutput(int[] stopoutput) {
        this.stopoutput = stopoutput;
    }

    public int[] getAtodest() {
        return atodest;
    }

    public void setAtodest(int[] atodest) {
        this.atodest = atodest;
    }

    public int getAtostoptime() {
        return atostoptime;
    }

    public void setAtostoptime(int atostoptime) {
        this.atostoptime = atostoptime;
    }

    public double getCurrent() {
        return current;
    }

    public void setCurrent(double current) {
        this.current = current;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getAtospeed() {
        return atospeed;
    }

    public void setAtospeed(double atospeed) {
        this.atospeed = atospeed;
    }

    public double[] getStoppos() {
        return stoppos;
    }

    public void setStoppos(double[] stoppos) {
        this.stoppos = stoppos;
    }

    public Location getLastsisign() {
        return lastsisign;
    }

    public void setLastsisign(Location lastsisign) {
        this.lastsisign = lastsisign;
    }

    public Location getLastspsign() {
        return lastspsign;
    }

    public void setLastspsign(Location lastspsign) {
        this.lastspsign = lastspsign;
    }

    public Location[] getResettablesisign() {
        return resettablesisign;
    }

    public void setResettablesisign(Location[] resettablesisign) {
        this.resettablesisign = resettablesisign;
    }

    public String getSignaltype() {
        return signaltype;
    }

    public void setSignaltype(String signaltype) {
        this.signaltype = signaltype;
    }

    public String getSignalorderptn() {
        return signalorderptn;
    }

    public void setSignalorderptn(String signalorderptn) {
        this.signalorderptn = signalorderptn;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public boolean isFreemode() {
        return freemode;
    }

    public void setFreemode(boolean freemode) {
        this.freemode = freemode;
    }

    public boolean isReqstopping() {
        return reqstopping;
    }

    public void setReqstopping(boolean reqstopping) {
        this.reqstopping = reqstopping;
    }

    public boolean isOverrun() {
        return overrun;
    }

    public void setOverrun(boolean overrun) {
        this.overrun = overrun;
    }

    public boolean isFixstoppos() {
        return fixstoppos;
    }

    public void setFixstoppos(boolean fixstoppos) {
        this.fixstoppos = fixstoppos;
    }

    public boolean isStaaccel() {
        return staaccel;
    }

    public void setStaaccel(boolean staaccel) {
        this.staaccel = staaccel;
    }

    public boolean isStaeb() {
        return staeb;
    }

    public void setStaeb(boolean staeb) {
        this.staeb = staeb;
    }

    public boolean isAtsbraking() {
        return atsbraking;
    }

    public void setAtsbraking(boolean atsbraking) {
        this.atsbraking = atsbraking;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isAtsping() {
        return atsping;
    }

    public void setAtsping(boolean atsping) {
        this.atsping = atsping;
    }

    public boolean isAtspnear() {
        return atspnear;
    }

    public void setAtspnear(boolean atspnear) {
        this.atspnear = atspnear;
    }

    public boolean isDoordiropen() {
        return doordiropen;
    }

    public void setDoordiropen(boolean doordiropen) {
        this.doordiropen = doordiropen;
    }

    public boolean isDoorconfirm() {
        return doorconfirm;
    }

    public void setDoorconfirm(boolean doorconfirm) {
        this.doorconfirm = doorconfirm;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public boolean isAllowatousage() {
        return allowatousage;
    }

    public void setAllowatousage(boolean allowatousage) {
        this.allowatousage = allowatousage;
    }

    public boolean isAtopisdirect() {
        return atopisdirect;
    }

    public void setAtopisdirect(boolean atopisdirect) {
        this.atopisdirect = atopisdirect;
    }

    public boolean isAtoforcebrake() {
        return atoforcebrake;
    }

    public void setAtoforcebrake(boolean atoforcebrake) {
        this.atoforcebrake = atoforcebrake;
    }

    public MinecartGroup getTrain() {
        return train;
    }

    public void setTrain(MinecartGroup train) {
        this.train = train;
    }

    public ItemStack[] getInv() {
        return inv;
    }

    public void setInv(ItemStack[] inv) {
        this.inv = inv;
    }

    public Player getP() {
        return p;
    }

    public Location[] getIlposlist() {
        return ilposlist;
    }

    public void setIlposlist(Location[] ilposlist) {
        this.ilposlist = ilposlist;
    }

    public long getIlenterqueuetime() {
        return ilenterqueuetime;
    }

    public void setIlenterqueuetime(long ilenterqueuetime) {
        this.ilenterqueuetime = ilenterqueuetime;
    }

    public Location[] getIlposoccupied() {
        return ilposoccupied;
    }

    public void setIlposoccupied(Location[] ilposoccupied) {
        this.ilposoccupied = ilposoccupied;
    }
}
