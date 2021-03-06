package com.golf2k18.states.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScalingViewport;
import com.golf2k18.function.Spline;
import com.golf2k18.io.DataIO;
import com.golf2k18.objects.Terrain;
import com.golf2k18.objects.Wall;
import com.golf2k18.states.State3D;
import com.golf2k18.StateManager;

import javax.swing.*;
import java.util.ArrayList;

/**
 * This class is used to create the course, with all its properties
 */
public class TerrainEditor extends State3D {
    private int startIndex;
    private Model nodeSelected;
    private Model nodeUnselected;
    private Model startNode;
    private Model holeNode;
    private Vector3 savedStart;
    private Vector3 savedHole;
    private Vector3 wallStart;
    private int savedWall;

    private final float NODE_DIAM = 0.2f;
    private boolean ctrl = false;
    private boolean change = false;
    private boolean setStart = false;
    private boolean setHole = false;
    private boolean setSize = false;
    private boolean setObstacles = false;
    private boolean deleteObstacles = false;
    private boolean skeletonView = false;

    private Spline function;
    private ArrayList<Integer> selected;
    private ArrayList<Wall> deletedObstacles = new ArrayList<>();

    private Stage hud;
    private Stage tempHUD;

    private InputProcessor thisProcessor = this;

    public TerrainEditor(StateManager manager, Terrain terrain) {
        super(manager, terrain);
    }

    /**
     * Creates the terrain according to the chosen function
     */
    @Override
    public void create() {
        if(!(terrain.getFunction() instanceof Spline)) {
            terrain.toSpline(1);
        }
        function = (Spline) terrain.getFunction();

        super.create();

        createNodes();
        initNodes();
        selected = new ArrayList<>();

        savedStart = terrain.getStart().cpy();
        savedHole = terrain.getHole().cpy();
        savedWall = terrain.getObstacles().size();

        createHUD();

        Gdx.input.setInputProcessor(new InputMultiplexer(this, controller, hud));

        controller.toggleScroll();

        createTempHud("obstacles");
    }

    private void createNodes(){
        ModelBuilder builder = new ModelBuilder();

        startNode = builder.createSphere(NODE_DIAM, NODE_DIAM, NODE_DIAM,
                50,50,
                new Material(ColorAttribute.createDiffuse(Color.GREEN)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates
        );

        holeNode = builder.createSphere(NODE_DIAM, NODE_DIAM, NODE_DIAM,
                50,50,
                new Material(ColorAttribute.createDiffuse(Color.RED)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates
        );

        nodeSelected = builder.createSphere(NODE_DIAM, NODE_DIAM, NODE_DIAM,
                50,50,
                new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates
        );

        nodeUnselected = builder.createSphere(NODE_DIAM, NODE_DIAM, NODE_DIAM,
                50,50,
                new Material(ColorAttribute.createDiffuse(Color.GRAY)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates
        );
        startIndex = instances.size();
    }

    private void initNodes(){
        while(instances.size() > startIndex) instances.remove(instances.size()-1);
        for (float i = 0; i <=terrain.getWidth() ; i++) {
            for (float j = 0; j <= terrain.getHeight() ; j++) {
                if(i == terrain.getStart().x && j == terrain.getStart().y){
                    instances.add(new ModelInstance(startNode,i,j,function.evaluateF(i,j)));
                }
                else if(i == terrain.getHole().x && j == terrain.getHole().y){
                    instances.add(new ModelInstance(holeNode,i,j,function.evaluateF(i,j)));
                }
                else instances.add(new ModelInstance(nodeUnselected,i,j,function.evaluateF(i,j)));
            }
        }
    }

    @Override
    public boolean scrolled(int amount) {
        if(!ctrl || selected.isEmpty() || skeletonView) return false;
        ArrayList<Vector3> newData = new ArrayList<>();
        for (Integer aSelected : selected) {
            if(!change) change = true;
            Vector3 pos = instances.get(startIndex + aSelected).transform.getTranslation(new Vector3());
            pos.z = function.evaluateF(pos.x, pos.y);
            pos.z -= amount;
            newData.add(pos);
            instances.get(startIndex + aSelected).transform.setTranslation(pos);
        }
        function.update(newData);
        super.createTerrain();
        return super.scrolled(amount);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if(button == 0) {
            if(deleteObstacles){
                int obstacle = getObject(screenX,screenY, getObstacles());
                if(obstacle >= 0) deletedObstacles.add(terrain.getObstacles().remove(obstacle));
                updateWalls();
                return super.touchDown(screenX, screenY, pointer, button);
            }
            int pointI = getObject(screenX, screenY,instances) - startIndex;
            if (!ctrl) {
                for (int i : selected) {
                    Vector3 pos = instances.get(i + startIndex).transform.getTranslation(new Vector3());
                    instances.set(i + startIndex, new ModelInstance(nodeUnselected, pos.x, pos.y, function.evaluateF(pos.x, pos.y)));
                }
                selected.clear();
            }
            if (pointI >= 0) {
                Vector3 pos = instances.get(pointI + startIndex).transform.getTranslation(new Vector3());
                if(setStart){
                    terrain.getStart().set(pos.x,pos.y,function.evaluateF(pos.x,pos.y));
                    initNodes();
                }
                else if(setHole){
                    terrain.getHole().set(pos.x,pos.y,function.evaluateF(pos.x,pos.y));
                    initNodes();
                }
                else if(setObstacles){
                    if(wallStart == null) wallStart = pos.cpy();
                    else {
                        terrain.getObstacles().add(new Wall(wallStart,pos.cpy()));
                        if(ctrl) wallStart = pos.cpy();
                        else wallStart = null;
                    }
                    updateWalls();
                }
                else{
                    instances.set(pointI + startIndex, new ModelInstance(nodeSelected, pos.x, pos.y, function.evaluateF(pos.x, pos.y)));
                    selected.add(pointI);
                }
            }
            else wallStart = null;
        }
        return super.touchDown(screenX, screenY, pointer, button);
    }

    @Override
    public boolean keyDown(int keycode) {
        if(keycode == Input.Keys.CONTROL_LEFT)ctrl = true;
        if(keycode == Input.Keys.S) setStart = true;
        if(keycode == Input.Keys.G) setHole = true;
        return super.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        if(keycode == Input.Keys.CONTROL_LEFT)ctrl = false;
        return super.keyUp(keycode);
    }

    /**
     * Converts the screen's (X,Y) coordinates into actual game coordinates for the camera
     * @param screenX x coordinate
     * @param screenY y coordinate
     * @return the game coordinates
     */
    private int getObject (int screenX, int screenY, ArrayList<ModelInstance> instances) {
        Ray ray = camera.getPickRay(screenX, screenY);

        int result = -1;
        float distance = -1;
        Vector3 position = new Vector3();

        for (int i = startIndex; i < instances.size(); ++i) {
            final ModelInstance point = instances.get(i);

            point.transform.getTranslation(position);

            final float len = ray.direction.dot(position.x-ray.origin.x, position.y-ray.origin.y, position.z-ray.origin.z);
            if (len < 0f)
                continue;

            float dist2 = position.dst2(ray.origin.x+ray.direction.x*len, ray.origin.y+ray.direction.y*len, ray.origin.z+ray.direction.z*len);
            if (distance >= 0f && dist2 > distance)
                continue;

            BoundingBox bounds = new BoundingBox();
            point.calculateBoundingBox(bounds);
            Vector3 dim = new Vector3();
            bounds.getDimensions(dim);
            float radius = dim.len() / 2f;
            if (dist2 <= radius * radius) {
                result = i;
                distance = dist2;
            }
        }
        return result;
    }

    @Override
    public void resize(int width, int height) {

    }

    @Override
    public void render() {
        super.render();
        if(!setObstacles && !setHole && !setSize && !setStart && !deleteObstacles && !skeletonView){
            hud.act();
            hud.draw();
        }
        else{
            tempHUD.act();
            tempHUD.draw();
        }
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    private void createHUD(){
        hud = new Stage(new ScalingViewport(Scaling.fit, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));

        Table content = new Table();
        content.setFillParent(true);

        content.add(new Label("TERRAIN EDITION",StateManager.skin,"title")).center().expandX().top().pad(10f).colspan(3);
        content.row();

        TextButton obstacles = new TextButton("Add obstacles",StateManager.skin);
        obstacles.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setObstacles = true;
                initNodes();
                createTempHud("OBSTACLE EDITION");
                Gdx.input.setInputProcessor(new InputMultiplexer(thisProcessor, controller ,tempHUD));
            }
        });
        content.add(obstacles).fillX().pad(10f).expandY().bottom();
        content.add();
        content.add();
        content.row();

        TextButton obstaclesDeletion = new TextButton("Delete obstacles",StateManager.skin);
        obstaclesDeletion.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                deleteObstacles = true;
                initNodes();
                createTempHud("OBSTACLE DELETE");
                Gdx.input.setInputProcessor(new InputMultiplexer(thisProcessor, controller ,tempHUD));
            }
        });
        content.add(obstaclesDeletion).fillX().pad(10f);

        content.add().padLeft(130f).padRight(130f);
        content.add();
        content.row();

        TextButton size = new TextButton("Modify terrain size",StateManager.skin);
        size.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setSize = true;
                initNodes();
                createTempHud("SIZE EDITION");
                Gdx.input.setInputProcessor(new InputMultiplexer(thisProcessor, controller ,tempHUD));
            }
        });
        content.add(size).fillX().pad(10f);

        content.add();
        Table nameTable = new Table();
        nameTable.add(new Label("Name: ",StateManager.skin));
        TextField nameField = new TextField("",StateManager.skin);
        nameTable.add(nameField).expandX().fillX().padLeft(10f);
        content.add(nameTable).fillX().pad(10f);
        content.row();

        TextButton start = new TextButton("Modify start position",StateManager.skin);
        start.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setStart = true;
                initNodes();
                createTempHud("START EDITION");
                Gdx.input.setInputProcessor(new InputMultiplexer(thisProcessor, controller ,tempHUD));
            }
        });
        content.add(start).fillX().pad(10f);

        TextButton walls = new TextButton(isHideWalls()?"Show obstacles":"Hide obstacles",StateManager.skin);
        walls.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggleHideWalls();
                walls.setText(isHideWalls()?"Show obstacles":"Hide obstacles");
            }
        });
        content.add(walls).fillX().pad(10f);

        TextButton save = new TextButton("Save",StateManager.skin);
        save.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String name = nameField.getText();
                if(name.length() > 0){
                    terrain.setName(name);
                    DataIO.writeTerrain(terrain);
                }
                else{
                    JOptionPane.showMessageDialog(null,"Please fill in a name","Error",JOptionPane.ERROR_MESSAGE);
                }
                initNodes();
            }
        });
        content.add(save).fillX().pad(10f);
        content.row();

        TextButton hole = new TextButton("Modify hole position",StateManager.skin);
        hole.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setHole = true;
                initNodes();
                createTempHud("HOLE EDITION");
                Gdx.input.setInputProcessor(new InputMultiplexer(thisProcessor, controller ,tempHUD));
            }
        });
        content.add(hole).fillX().pad(10f);

        TextButton skeleton = new TextButton("Show terrain skeleton",StateManager.skin);
        skeleton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggleSkeleton();
                if(!isHideWalls()) toggleHideWalls();
                skeletonView = true;
                initNodes();
                createTempHud("TERRAIN SKELETON");
                Gdx.input.setInputProcessor(new InputMultiplexer(thisProcessor, controller ,tempHUD));
            }
        });
        content.add(skeleton).fillX().pad(10f);

        TextButton home = new TextButton("Home",StateManager.skin);
        home.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if(change) {
                    int n = JOptionPane.showConfirmDialog(
                            null,
                            "There are some unsaved changes.\nReturn?",
                            "Warning",
                            JOptionPane.YES_NO_OPTION);
                    if(n == 0) manager.home();
                }
                else manager.home();
            }
        });
        content.add(home).fillX().pad(10f).right();

        hud.addActor(content);
    }

    private void createTempHud(String title){
        tempHUD = new Stage(new ScalingViewport(Scaling.fit, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));

        Table content = new Table();
        content.setFillParent(true);
        content.add(new Label(title,StateManager.skin,"title")).colspan(2);
        content.row();

        TextButton cancel = new TextButton("Cancel",StateManager.skin);
        cancel.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if(setObstacles){
                    ArrayList<Wall> walls = terrain.getObstacles();
                    int i = walls.size()-1;
                    while(walls.size() > savedWall && i >= 0){
                        walls.remove(i);
                        i--;
                    }
                    updateWalls();
                    setObstacles = false;
                }
                if (deleteObstacles){
                    while(!deletedObstacles.isEmpty()){
                        terrain.getObstacles().add(deletedObstacles.remove(deletedObstacles.size()-1));
                    }
                    deleteObstacles = false;
                }
                if(setHole){
                    terrain.getHole().set(savedHole.x, savedHole.y,function.evaluateF(savedHole.x, savedHole.y));
                    initNodes();
                    setHole = false;
                }
                if(setStart){
                    terrain.getStart().set(savedStart.x,savedStart.y,function.evaluateF(savedStart.x,savedStart.y));
                    initNodes();
                    setStart = false;
                }
                if(skeletonView){
                    toggleSkeleton();
                    skeletonView = false;
                }
                setSize = false;
                Gdx.input.setInputProcessor(new InputMultiplexer(thisProcessor, controller ,hud));
            }
        });
        content.add(cancel).expandY().fillX().pad(10f).bottom();

        if(!skeletonView){
            TextButton apply = new TextButton("Apply",StateManager.skin);
            apply.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if(setObstacles){
                        savedWall = terrain.getObstacles().size();
                        setObstacles = false;
                    }
                    if(deleteObstacles){
                        deletedObstacles.clear();
                        deleteObstacles = false;
                    }
                    if(setHole){
                        savedHole = terrain.getHole().cpy();
                        setHole = false;
                    }
                    if(setStart){
                        savedStart = terrain.getStart().cpy();
                        setStart = false;
                    }
                    if(skeletonView){
                        toggleSkeleton();
                        skeletonView = false;
                    }
                    setSize = false;

                    Gdx.input.setInputProcessor(new InputMultiplexer(thisProcessor, controller ,hud));
                }
            });
            content.add(apply).fillX().pad(10f).bottom();
            content.row();

            TextButton walls = new TextButton(isHideWalls()?"Show obstacles":"Hide obstacles",StateManager.skin);
            walls.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    toggleHideWalls();
                    walls.setText(isHideWalls()?"Show obstacles":"Hide obstacles");
                }
            });
            content.add(walls).fillX().colspan(2).pad(10f);
        }

        tempHUD.addActor(content);
    }
}
