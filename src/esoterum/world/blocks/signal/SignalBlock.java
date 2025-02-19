package esoterum.world.blocks.signal;

import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Log;
import arc.util.io.*;
import esoterum.*;
import esoterum.graph.*;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.world.Block;

public class SignalBlock extends Block
{

    public TextureRegion bottomRegion, baseRegion, signalRegion, shieldRegion;

    public TextureRegion[] inputSignalRegions, outputSignalRegions, shieldRegions;

    public boolean debugDraw = false;
    public int[] inputs = new int[0];
    public int[] outputs = new int[0];
    public int vertexCount = 1;
    public int[] conns = new int[0];
    public boolean hasGraph = true;

    public SignalBlock(String name)
    {
        super(name);

        configurable = true;
        update = true;
        solid = true;
        health = 60;

        category = Category.logic;

        config(Long.class, (SignalBuild tile, Long i) -> {
            // Log.info("toggle shielding " + i);
            tile.shielding = tile.shielding ^ i;
            tile.updateEdges();
            //Log.info("config Long");
        });

        config(Object[].class, (SignalBuild tile, Object[] p) -> {
            if (p[0] instanceof Long l)
            {
                // Log.info("set shielding " + l);
                tile.shielding = l;
                tile.updateEdges();
            }
            //Log.info("config Object[]");
        });
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid)
    {
        super.drawPlace(x, y, rotation, valid);
    }

    public void setInputs(int... indices)
    {
        inputs = indices;
    }

    public void setOutputs(int... indices)
    {
        outputs = indices;
    }

    public void setConns(int... vc)
    {
        conns = vc;
    }

    @Override
    public void load()
    {
        super.load();

        uiIcon = fullIcon = Core.atlas.find(name + "-full");

        bottomRegion = Core.atlas.find(name + "-bottom", "eso-none");

        String[] bases = {"eso-base-square", "eso-mega-base-square", "eso-none", "eso-giga-base-square", "eso-none", "eso-none", "eso-none", "eso-tera-base-square"};
        baseRegion = Core.atlas.find(name + "-base", bases[size-1]);

        signalRegion = Core.atlas.find(name + "-signal", "eso-none");

        shieldRegions = new TextureRegion[16]; // pre-generated shielding for 1x1 blocks
        shieldRegion = Core.atlas.find("eso-shielding", "eso-none");
        for (int i = 0; i < 16; i++) shieldRegions[i] = Core.atlas.find("eso-shielding-" + i, "eso-none");

        inputSignalRegions = new TextureRegion[size * 4];
        outputSignalRegions = new TextureRegion[size * 4];
        for (int i = 0; i < size * 4; i++)
        {
            if (inputs[i] > 0) inputSignalRegions[i] = Core.atlas.find(name + "-input-" + i, "eso-none");
            if (outputs[i] > 0) outputSignalRegions[i] = Core.atlas.find(name + "-output-" + i, "eso-none");
        }
    }

    @Override
    public void init()
    {
        super.init();

        if (inputs == null)
        {
            inputs = new int[size * 4];
            for (int i = 0; i < size * 4; i++)
            {
                inputs[i] = 1;
            }
        }
        if (outputs == null)
        {
            outputs = new int[size * 4];
            for (int i = 0; i < size * 4; i++)
            {
                outputs[i] = 1;
            }
        }
        if (conns == null)
        {
            conns = new int[size * 4];
            for (int i = 0; i < size * 4; i++)
            {
                conns[i] = 0;
            }
        }
    }

    @Override
    public Object pointConfig(Object config, arc.func.Cons<Point2> transformer)
    {
        if (config instanceof Object[] configs)
        {
            Object[] result = new Object[configs.length];
            for (int i = 0; i < configs.length; i++)
            {
                result[i] = BuildPlan.pointConfig(this, configs[i], transformer);
            }
            return result;
        }
        return config;
    }

    public Color getWireOffColor()
    {
        return Color.white;
    }

    public Color getWireColor()
    {
        return Pal.accent;
    }

    public class SignalBuild extends Building
    {
        public ConnVertex[] v = new ConnVertex[vertexCount];
        public EulerTourNode[] r = new EulerTourNode[vertexCount];
        public int[] signal = new int[vertexCount];
        public boolean[] active = new boolean[size * 4];
        public long shielding;

        public int[] inputs()
        {
            return inputs;
        }

        public int[] outputs()
        {
            return outputs;
        }

        public int[] conns()
        {
            return conns;
        }

        public int size()
        {
            return size;
        }

        @Override
        public void created()
        {
            super.created();
            if (!this.block.rotate) rotation(0);

            for (int i = 0; i < vertexCount; i++)
            {
                if (v[i] == null) SignalGraph.addVertex(this, i);
            }

            updateEdges();
        }

        public void updateEdges()
        {
            for (int i = 0; i < vertexCount; i++) SignalGraph.clearEdges(v[i]);
            for (int i = 0; i < size * 4; i++)
            {
                active[i] = false;
                Vec2 offset = EdgeUtils.getEdgeOffset(size, i, rotation);
                Vec2 sideOffset = EdgeUtils.getEdgeOffset(1, i / size, rotation);
                if (Vars.world.build((int) (x / 8 + offset.x + sideOffset.x), (int) (y / 8 + offset.y + sideOffset.y)) instanceof SignalBuild b)
                {
                    int index = EdgeUtils.getOffsetIndex(b.size(), x / 8 + offset.x - b.x / 8, y / 8 + offset.y - b.y / 8, b.rotation);
                    if (((b.inputs()[index] & outputs[i]) == 1 || (b.outputs()[index] & inputs[i]) == 1) && ((shielding & (1l << i)) == 0) && ((b.shielding & (1l << index)) == 0))
                    {
                        SignalGraph.addEdge(v[conns[i]], b.v[b.conns()[index]]);
                        active[i] = true;
                    }
                    b.active[index] = active[i];
                }
            }
            SignalGraph.needsUpdate = SignalGraph.vertices;
        }

        @Override
        public void onRemoved()
        {
            for (int i = 0; i < vertexCount; i++) SignalGraph.removeVertex(this, i);
            super.onRemoved();
            SignalGraph.needsUpdate = SignalGraph.vertices;
        }

        @Override
        public void updateTile()
        {
            if (SignalGraph.needsUpdate > 0)
            {
                for (int i = 0; i < vertexCount; i++) r[i] = SignalGraph.graph.vertexInfo.get(v[i]).vertex.arbitraryVisit.root();
                SignalGraph.needsUpdate -= vertexCount;
            }
            for (int i = 0; i < vertexCount; i++) if (r[i] != null) signal[i] = (int) r[i].augmentation;
            // for (int i = 0; i < vertexCount; i++) signal[i] = (int) SignalGraph.graph.getComponentAugmentation(v[i]);
        }

        // @Override
        // public void onProximityUpdate()
        // {
        //     super.onProximityUpdate();
        //     updateEdges();
        // }

        // @Override
        // public void updateProximity()
        // {
        //     super.updateProximity();
        //     updateEdges();
        // }

        @Override
        public void draw()
        {

            if (EsoVars.drawSignalRegions)
            {
                //Draw.rect(bottomRegion, x, y);
                Draw.rect(baseRegion, x, y);

                // drawShieldRegions();
                // drawSignalRegions();
            }
            else
            {
                Draw.rect(uiIcon, x, y, rotation * 90);
            }
        }

        @Override
        public void drawSelect()
        {
            super.drawSelect();
            if (!EsoVars.drawSignalRegions) drawShieldRegions();
            if (!EsoVars.drawSignalRegions) drawSignalRegions(Core.camera.bounds(new Rect()));
        }

        public void drawSignalRegions(Rect camera)
        {
            Draw.color(signal[0] == 1 ? getWireColor() : getWireOffColor());

            Draw.rect(signalRegion, x, y, rotation * 90);

            for (int i = 0; i < size * 4; i++)
            {
                if (active[i])
                {
                    Draw.color(signal[conns[i]] == 1 ? getWireColor() : getWireOffColor());
                    if (inputs[i] == 1) Draw.rect(inputSignalRegions[i], x, y, rotation * 90);
                    else if (outputs[i] == 1) Draw.rect(outputSignalRegions[i], x, y, rotation * 90);
                }
            }
        }

        // full shield drawing code in SignalMem
        public void drawShieldRegions()
        {
            Draw.rect(shieldRegions[(int)shielding & 15], x, y, rotation * 90);
        }

        @Override
        public void buildConfiguration(Table table)
        {
            table.table().size(40f);
            for (int i = size * 6 - rotation * size - 1; i >= size * 5 - rotation * size; i--)
            {
                final int t = i % (size * 4);
                TextButton b = table.button((shielding & (1l << t)) > 0 ? "" + t : "X", () -> {
                    configure(1l << t);
                }).size(40f).tooltip("Toggle Shielding").get();
                b.update(() -> {
                    b.setText((shielding & (1l << t)) > 0 ? "" + t : "X");
                });
            }
            table.row();
            for (int i = 0; i < size; i++)
            {
                final int t1 = (6 * size + i - rotation * size) % (size * 4);
                TextButton b1 = table.button((shielding & (1l << t1)) > 0 ? "" + t1 : "X", () -> {
                    configure(1l << t1);
                }).size(40f).tooltip("Toggle Shielding").get();
                b1.update(() -> {
                    b1.setText((shielding & (1l << t1)) > 0 ? "" + t1 : "X");
                });
                for (int j = 0; j < size; j++) table.table().size(40f);
                final int t2 = (size * 5 - i - 1 - rotation * size) % (size * 4);
                TextButton b2 = table.button((shielding & (1l << t2)) > 0 ? "" + t2 : "X", () -> {
                    configure(1l << t2);
                }).size(40f).tooltip("Toggle Shielding").get();
                b2.update(() -> {
                    b2.setText((shielding & (1l << t2)) > 0 ? "" + t2 : "X");
                });
                table.row();
            }
            table.table().size(40f);
            for (int i = size * 7 - rotation * size; i < size * 8 - rotation * size; i++)
            {
                final int t = i % (size * 4);
                TextButton b = table.button((shielding & (1l << t)) > 0 ? "" + t : "X", () -> {
                    configure(1l << t);
                }).size(40f).tooltip("Toggle Shielding").get();
                b.update(() -> {
                    b.setText((shielding & (1l << t)) > 0 ? "" + t : "X");
                });
            }
        }

        @Override
        public void updateTableAlign(Table table)
        {
            Vec2 pos = Core.input.mouseScreen(x, y);
            table.setPosition(pos.x, pos.y, Align.center);
        }

        @Override
        public Object[] config()
        {
            return new Object[]{shielding};
        }

        @Override
        public byte version()
        {
            return 4;
        }

        @Override
        public void write(Writes write)
        {
            super.write(write);
            write.l(shielding);
            for (int i = 0; i < vertexCount; i++) write.i(signal[i]);
        }

        @Override
        public void read(Reads read, byte revision)
        {
            super.read(read, revision);
            if (revision >= 4)
            {
                shielding = read.l();
                for (int i = 0; i < vertexCount; i++) signal[i] = read.i();
                updateEdges();
            }
            else if (revision == 3)
            {
                for (int i = 0; i < vertexCount; i++) signal[i] = read.i();
            }
            else if (revision == 1 && hasGraph)
            {
                read.i();
                read.bool();
            }
        }
    }
}
