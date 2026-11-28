package com.AnnindaRajDas.gravityshift.level;

import com.AnnindaRajDas.gravityshift.level.ChamberDefinition.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and validates {@link ChamberDefinition} instances from Tiled {@code .tmx} XML files.
 * Merges adjacent horizontal solid tiles into unified rectangles (seam merging).
 */
public final class TmxChamberLoader {

    private TmxChamberLoader() {}

    public static String getChamberPath(int chamberNumber) {
        if (chamberNumber < 1 || chamberNumber > 8) {
            throw new IllegalArgumentException("Chamber number must be 1..8, got: " + chamberNumber);
        }
        return String.format("levels/chamber_%02d.tmx", chamberNumber);
    }

    public static ChamberDefinition loadChamber(int chamberNumber) {
        String path = getChamberPath(chamberNumber);
        if (Gdx.files != null) {
            FileHandle handle = Gdx.files.internal(path);
            if (handle.exists()) {
                try (InputStream in = handle.read()) {
                    return parse(in, chamberNumber);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to load TMX " + path + ": " + e.getMessage(), e);
                }
            }
        }

        // Headless fallback for JUnit tests
        File local = new File("assets/" + path);
        if (!local.exists()) {
            local = new File("../assets/" + path);
        }
        if (!local.exists()) {
            throw new IllegalArgumentException("Missing TMX chamber file: " + path);
        }
        try (InputStream in = new FileInputStream(local)) {
            return parse(in, chamberNumber);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load TMX " + path + ": " + e.getMessage(), e);
        }
    }

    public static ChamberDefinition parse(InputStream in, int chamberNumber) {
        if (in == null) {
            throw new IllegalArgumentException("InputStream cannot be null.");
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            Element map = doc.getDocumentElement();
            if (!"map".equals(map.getTagName())) {
                throw new IllegalArgumentException("Root element must be <map>.");
            }

            int mapWidth = parseIntAttr(map, "width");
            int mapHeight = parseIntAttr(map, "height");
            float tileWidth = parseFloatAttr(map, "tilewidth");
            float tileHeight = parseFloatAttr(map, "tileheight");
            if (mapWidth <= 0 || mapHeight <= 0 || tileWidth <= 0 || tileHeight <= 0) {
                throw new IllegalArgumentException("Invalid map dimensions in TMX.");
            }

            String chamberName = "Chamber " + chamberNumber;
            NodeList mapProps = map.getElementsByTagName("property");
            for (int i = 0; i < mapProps.getLength(); i++) {
                Element prop = (Element) mapProps.item(i);
                if (prop.getParentNode() != null && prop.getParentNode().getParentNode() == map
                        && "name".equals(prop.getAttribute("name"))) {
                    chamberName = prop.getAttribute("value");
                }
            }

            // 1. Parse solid tile layer with horizontal seam merging
            List<RectSpec> solids = new ArrayList<>();
            NodeList layers = map.getElementsByTagName("layer");
            for (int i = 0; i < layers.getLength(); i++) {
                Element layer = (Element) layers.item(i);
                if ("solids".equals(layer.getAttribute("name"))) {
                    parseSolidsLayer(layer, mapWidth, mapHeight, solids);
                }
            }

            // 2. Parse objects
            Float spawnX = null;
            Float spawnY = null;
            RectSpec exit = null;
            List<CubeSpec> cubes = new ArrayList<>();
            List<PlateSpec> plates = new ArrayList<>();
            List<DoorSpec> doors = new ArrayList<>();
            List<HazardSpec> hazards = new ArrayList<>();

            NodeList objects = map.getElementsByTagName("object");
            for (int i = 0; i < objects.getLength(); i++) {
                Element obj = (Element) objects.item(i);
                String type = obj.getAttribute("type");
                float ox = parseFloatAttr(obj, "x") / tileWidth;
                float oyPx = parseFloatAttr(obj, "y");
                float ow = obj.hasAttribute("width") ? parseFloatAttr(obj, "width") / tileWidth : 1.0f;
                float ohPx = obj.hasAttribute("height") ? parseFloatAttr(obj, "height") : tileHeight;
                float oh = ohPx / tileHeight;
                // Convert Tiled top-down Y to Box2D bottom-up Y
                float oy = mapHeight - (oyPx + ohPx) / tileHeight;

                switch (type) {
                    case "spawn" -> {
                        spawnX = ox;
                        spawnY = oy;
                    }
                    case "exit" -> {
                        if (ow <= 0 || oh <= 0) {
                            throw new IllegalArgumentException("Exit must have positive dimensions.");
                        }
                        exit = new RectSpec(ox, oy, ow, oh);
                    }
                    case "cube" -> cubes.add(new CubeSpec(ox, oy));
                    case "plate" -> {
                        if (ow <= 0 || oh <= 0) {
                            throw new IllegalArgumentException("Plate must have positive dimensions.");
                        }
                        String channel = getObjectProperty(obj, "channel");
                        if (channel == null || channel.isEmpty()) {
                            throw new IllegalArgumentException("Plate missing required 'channel' property.");
                        }
                        plates.add(new PlateSpec(ox, oy, ow, oh, channel));
                    }
                    case "door" -> {
                        if (ow <= 0 || oh <= 0) {
                            throw new IllegalArgumentException("Door must have positive dimensions.");
                        }
                        String channel = getObjectProperty(obj, "channel");
                        if (channel == null || channel.isEmpty()) {
                            throw new IllegalArgumentException("Door missing required 'channel' property.");
                        }
                        doors.add(new DoorSpec(ox, oy, ow, oh, channel));
                    }
                    case "laser" -> {
                        if (ow <= 0 || oh <= 0) {
                            throw new IllegalArgumentException("Laser must have positive dimensions.");
                        }
                        String channel = getObjectProperty(obj, "channel");
                        String activeStr = getObjectProperty(obj, "active");
                        boolean active = activeStr == null || Boolean.parseBoolean(activeStr);
                        hazards.add(new HazardSpec(ox, oy, ow, oh, channel, active));
                    }
                    default -> throw new IllegalArgumentException("Unknown TMX object type: " + type);
                }
            }

            if (spawnX == null || spawnY == null) {
                throw new IllegalArgumentException("Chamber " + chamberNumber + " missing required 'spawn' object.");
            }
            if (exit == null) {
                throw new IllegalArgumentException("Chamber " + chamberNumber + " missing required 'exit' object.");
            }

            return new ChamberDefinition(
                    chamberNumber, chamberName, spawnX, spawnY, exit,
                    solids, cubes, plates, doors, hazards);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed TMX XML: " + e.getMessage(), e);
        }
    }

    private static void parseSolidsLayer(Element layer, int mapWidth, int mapHeight, List<RectSpec> solids) {
        NodeList dataNodes = layer.getElementsByTagName("data");
        if (dataNodes.getLength() == 0) {
            return;
        }
        String csv = dataNodes.item(0).getTextContent().trim();
        if (csv.isEmpty()) {
            return;
        }
        String[] tokens = csv.split("[,\\s]+");
        if (tokens.length != mapWidth * mapHeight) {
            throw new IllegalArgumentException(
                    "Solids layer tile count mismatch: expected " + (mapWidth * mapHeight) + ", got " + tokens.length);
        }

        for (int row = 0; row < mapHeight; row++) {
            int runStart = -1;
            for (int col = 0; col <= mapWidth; col++) {
                boolean solid = false;
                if (col < mapWidth) {
                    int gid = Integer.parseInt(tokens[row * mapWidth + col]);
                    solid = gid > 0;
                }
                if (solid && runStart == -1) {
                    runStart = col;
                } else if (!solid && runStart != -1) {
                    float x = runStart;
                    float y = mapHeight - 1 - row;
                    float w = col - runStart;
                    solids.add(new RectSpec(x, y, w, 1.0f));
                    runStart = -1;
                }
            }
        }
    }

    private static String getObjectProperty(Element obj, String propName) {
        NodeList props = obj.getElementsByTagName("property");
        for (int i = 0; i < props.getLength(); i++) {
            Element prop = (Element) props.item(i);
            if (propName.equals(prop.getAttribute("name"))) {
                return prop.getAttribute("value");
            }
        }
        return null;
    }

    private static int parseIntAttr(Element el, String attr) {
        if (!el.hasAttribute(attr)) {
            throw new IllegalArgumentException("Missing attribute '" + attr + "' on <" + el.getTagName() + ">");
        }
        return Integer.parseInt(el.getAttribute(attr));
    }

    private static float parseFloatAttr(Element el, String attr) {
        if (!el.hasAttribute(attr)) {
            throw new IllegalArgumentException("Missing attribute '" + attr + "' on <" + el.getTagName() + ">");
        }
        return Float.parseFloat(el.getAttribute(attr));
    }
}
