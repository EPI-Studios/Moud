package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Vec3;

// a box cut out of a sheet
//
// this is what a body is made of, and it is a class rather than a name in a table because of what
// the table cost: whether a part was drawn through the sheet or drawn flat was decided by looking
// its name up, so everything the table did not name -- a cape, a wing, a plate of armour, a worn
// head, a pair of ears -- was drawn twice. once white by the ordinary batch, once properly by the
// skin batch, and whichever wrote depth second won. a limb answers for itself instead
//
// it also means a place can hang a seventh one on a body and have it textured, which a table keyed
// on six names could never do
public class Limb extends Part {

    // how far this limb swings with a walk, in radians at full speed, and where in the stride
    //
    // the whole point of these two: the engine's walk used to be six names written into it, so a
    // seventh limb was textured, posable and jointed and then stood perfectly still while the body
    // walked out from under it. a tail is a limb that swings a little, out of phase with the legs,
    // and saying so should be two numbers rather than a change to the engine

    @Prop(min = 0) public double swing;

    // in turns, so a half is the opposite leg. anything between is a lag
    public double swingPhase;

    // side to side rather than fore and aft, which is what a tail does and a leg does not
    public boolean swingSideways;

    // the origin of the rect this box is cut from, in texels of its sheet
    public double u;
    public double v;

    // how big the box is on the sheet, in texels
    //
    // not how big it is drawn. the game grows a wing by a texel on every side and an ear by one,
    // and leaves the rect sized for the box before it grew -- so the two are separate numbers and
    // always will be. a renderer that derives its rect from the drawn size is wrong on every
    // grown box there is
    public Vec3 texels = Vec3.ZERO;

    // how big that sheet is. a skin is sixty four square, an elytra and a piece of armour are
    // sixty four by thirty two, a parrot is thirty two square
    @Prop(min = 1) public double sheetWidth = 64;
    @Prop(min = 1) public double sheetHeight = 64;

    // which sheet, or empty for the body's own. what a place asks for, and otherwise what the
    // client resolves from the player wearing it
    public String sheet = "";

    // the same rect read the other way round in x. the game builds the left arm and the left leg
    // of every armour piece, and the right wing of an elytra, as mirrors of their opposites
    public boolean mirrored;

    // a blank texel is nothing rather than black
    //
    // true for everything worn: a hat, a sleeve, a plate of armour, a wing. the base six are the
    // only layer a skin draws solid, and treating a hat as solid is the black box every bad skin
    // renderer puts on somebody's head
    public boolean cutout;
}
