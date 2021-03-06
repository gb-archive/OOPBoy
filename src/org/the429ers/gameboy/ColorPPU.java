package org.the429ers.gameboy;

import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ColorPPU implements IPPU, Serializable {
    private MMU mem;
    private LCDControl lcdControl;
    private ColorPaletteManager backgroundColorPaletteManager;
    private ColorPaletteManager spriteColorPaletteManager;
    private TileSetManager tileSetManager;
    private SpriteManager spriteManager;
    private int currentX;
    private int currentY;
    private int scrollX;
    private int scrollY;
    private int cycleCount;
    private boolean drewFrame;
    private GameBoyScreen gbs;
    private int LYCompare;
    private int windowX;
    private int windowY;
    private Map<Integer, IColorSprite> sprites;
    private ColorTileMap background;
    private ColorTileMap window;
    private transient BufferedImage frame;
    private boolean hBlank;
    private boolean vBlank;
    private boolean disabledLastTick;
    
    public ColorPPU(MMU mem, GameBoyScreen gbs) {
        this.mem = mem;
        lcdControl = new LCDControl(mem);
        this.gbs = gbs;
        frame = new BufferedImage(160, 144, BufferedImage.TYPE_3BYTE_BGR);
        sprites = new HashMap<>();
        tileSetManager = new TileSetManager(true);
        mem.setTileSetManager(tileSetManager);
        spriteManager = new SpriteManager(mem, tileSetManager, lcdControl);
        spriteManager.initializeSprites();
        mem.setSpriteManager(spriteManager);
    }
    
    public void toggleHBlankIndicator() {
        hBlank = false;
    }
    
    
    public void loadMap() {
        int address = lcdControl.isUse9800TileMapAddressingForBackground() ? 0x9800 : 0x9c00;
        int tileSetNum = lcdControl.isUse8000TileDataForWindowAndBackground() == true ? 0 : 1;
        background = new ColorTileMap(mem, address, tileSetManager, tileSetNum);
    }
    
    public void loadWindow() {
        int address = lcdControl.isUse9800TileMapAddressingForWindow() ? 0x9800 : 0x9c00;
        int tileSetNum = lcdControl.isUse8000TileDataForWindowAndBackground() == true ? 0 : 1;
        window = new ColorTileMap(mem, address, tileSetManager, tileSetNum);
    }
    
    public void setPaletteManagers(ColorPaletteManager background, ColorPaletteManager sprites) {
        this.backgroundColorPaletteManager = background;
        this.spriteColorPaletteManager = sprites;
    }

    public void tick() {
        lcdControl.update();
        if (!lcdControl.isDisplayEnabled()) {
            disabledLastTick = true;
            return;
        }
        if (disabledLastTick) {
            loadMap();
            disabledLastTick = false;
        }
        
    
        scrollX = mem.readByte(0xFF43);
        if (cycleCount == OAM_SEARCH_START) {
            if (!lcdControl.isBackgroundDisplay()) {
                System.out.println("turn off the bg");
            }
            hBlank = false;
            scrollY = mem.readByte(0xFF42);
            if (currentY < ACTUAL_LINES) {
                int status = mem.readByte(0xFF41) & 0x3F;
                mem.writeByte(0xFF41, status | 0x80);
            }
            mem.writeByte(0xFF44, currentY);
            if (currentY == 0) {
                this.loadMap();
                vBlank = false;
                //this.tileSetManager.updateTileSets();
            }
            if (lcdControl.isSpritesEnabled()) {
                loadSprites();
            }
            //loadWindow(useTileSet1, useWindowTileMap1);
            windowX = mem.readByte(0xff4b) - 7;
            windowY = mem.readByte(0xff4a);
            currentX = 0;
            scrollX = mem.readByte(0xFF43);
            loadWindow();
        }
        if (cycleCount == PIXEL_TRANSFER_START) {
            int status = mem.readByte(0xFF41) & 0x3F;
            mem.writeByte(0xFF41, status | 0xC0);
        }
        
        
        
        // Actually transfer pixels
        if (cycleCount >= PIXEL_TRANSFER_START && cycleCount < PIXEL_TRANSFER_START + 160 && currentY < ACTUAL_LINES) {
            int yPos = currentY + scrollY;
            int xPos = scrollX + currentX;
            Tile currentTile;
            ColorPalette currentPalette;
            int pixel;
            Tile backgroundTile = background.getTile(yPos / 8, xPos / 8);
            if (lcdControl.isWindowEnabled() && currentX >= windowX && currentY >= windowY) {
                Tile windowTile = window.getTile((currentY - windowY) / 8, (currentX - windowX) / 8);
                int windowPixel = windowTile.getPixel((currentY - windowY)  % 8, (currentX - windowX) % 8);
                if (lcdControl.isSpritesEnabled() && sprites.containsKey(currentX + 8) && !window.hasPriority((currentY - windowY) / 8, (currentX - windowX) / 8)) {
                    IColorSprite currentSprite = sprites.get(currentX + 8);
                    int spritePixel = currentSprite.getPixel(currentY - (currentSprite.getSpriteY() - 16), currentX - (currentSprite.getSpriteX() - 8));
                    if ((currentSprite.getPriority() == 0 || windowPixel == 0) && spritePixel != 0) {
                        //currentTile = currentSprite.getTile();
                        currentPalette = spriteColorPaletteManager.getPalette(currentSprite.getPaletteNumber());
                        pixel = spritePixel;
                    }
                    else {
                        currentTile = windowTile;
                        currentPalette = backgroundColorPaletteManager.getPalette(window.getPaletteNumber((currentY - windowY)  % 8, (currentX - windowX) % 8));
                        pixel = windowPixel;
                    }
                }
                else {
                    currentTile = windowTile;
                    currentPalette = backgroundColorPaletteManager.getPalette(window.getPaletteNumber((currentY - windowY)  % 8, (currentX - windowX) % 8));
                    pixel = windowPixel;
                }
            }
            else if (lcdControl.isSpritesEnabled() && sprites.containsKey(currentX + 8)) {
                IColorSprite currentSprite = sprites.get(currentX + 8);
                int spritePixel = currentSprite.getPixel(currentY - (currentSprite.getSpriteY() - 16), currentX - (currentSprite.getSpriteX() - 8));
                if ((currentSprite.getPriority() == 0 || backgroundTile.getPixel(yPos % 8, xPos % 8) == 0) && spritePixel != 0) {
                    //currentTile = currentSprite.getTile();
                    currentPalette = spriteColorPaletteManager.getPalette(currentSprite.getPaletteNumber());
                    pixel = spritePixel;

                }
                else {
                    currentTile = backgroundTile;
                    currentPalette = backgroundColorPaletteManager.getPalette(background.getPaletteNumber(yPos / 8, xPos / 8));
                    pixel = currentTile.getPixel(yPos % 8, xPos % 8);
                }
            }
            else {
                currentTile = backgroundTile;
                currentPalette = backgroundColorPaletteManager.getPalette(background.getPaletteNumber(yPos / 8, xPos / 8));
                pixel = currentTile.getPixel(yPos % 8, xPos % 8);
            }
            if (frame == null) {
                frame = new BufferedImage(160, 144, BufferedImage.TYPE_3BYTE_BGR);
            }

            frame.setRGB(currentX, currentY, currentPalette.getColor(pixel).getRGB());
            currentX++;
        }
        // H-Blank Interrupt
        if (cycleCount == H_BLANK_START && currentY < ACTUAL_LINES) {
            hBlank = true;
            int status = mem.readByte(0xFF41) & 0x3F;
            mem.writeByte(0xFF41, status | 0xC0);
        }
        
        
        // Increment currentY
        if (cycleCount == H_BLANK_END) {
            currentY++;
            if (currentY == 154) {
                currentY = 0;
            }
        }
        
        drewFrame = false;
        // Send V-Blank interrupt
        if (currentY == 145 && cycleCount == 0) {
            vBlank = true;
            drewFrame = true;
            drawFrame();
        }
        
        //send LCDC interrupt
        if (currentY == LYCompare){
            int interruptRegister = mem.readByte(0xFF0F) & 0xFE;
            mem.writeByte(0xFF0F, interruptRegister | 0x02);
        }
        
        cycleCount++;
        cycleCount %= LINE_LENGTH;
    }
    
    
    private void drawFrame() {
        int status = mem.readByte(0xFF41) & 0x3F;
        mem.writeByte(0xFF41, status | 0x40);
        gbs.drawFrame(frame);
        drewFrame = true;
        int interruptRegister = mem.readByte(0xFF0F) & 0xFE;
        mem.writeByte(0xFF0F, interruptRegister | 0x01);
        //mem.writeByte(0xFF85, 0xFF);
        //mem.writeByte(0xFF44, 0x90);
    }
    
    public void loadSprites() {
        sprites.clear();
        int spriteCount = 0;
        int spritesFound = 0;
        int memAddress = 0xFE00;
        while (spriteCount < 40 && spritesFound < 10) {
            IColorSprite s = null;
            if (!lcdControl.isUseSmallSprites()) {
                s = new ColorLargeSprite(mem, memAddress, tileSetManager);
            }
            else {
                s = new ColorSmallSprite(mem, memAddress, tileSetManager);
            }
            //IColorSprite s = spriteManager.getSprite(spriteCount);
            if (s.inRange(currentY + 16)) {
                spritesFound++;
                for (int i = 0; i < 8; i++) {
                    if (!sprites.containsKey(s.getSpriteX() + i)) {
                        if (s.getPixel(currentY - (s.getSpriteY() - 16), i) != 0) {
                            sprites.put(s.getSpriteX() + i, s);
                        }
                    }
                    else {
                        IColorSprite conflictSprite = sprites.get(s.getSpriteX() + i);
                        boolean isTransparent = s.getPixel(currentY - (s.getSpriteY() - 16), i) == 0;
                        if (s.getSpriteX() < conflictSprite.getSpriteX() && !isTransparent) {
                            sprites.put(s.getSpriteX() + i, s);
                        }
                    }
                }
            }
            spriteCount++;
            memAddress += 4;
        }
    }


    @Override
    public boolean drewFrame() {
        // TODO Auto-generated method stub
        return drewFrame;
    }


    @Override
    public boolean isHBlank() {
        // TODO Auto-generated method stub
        return hBlank;
    }


    @Override
    public void loadTileSets() {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void loadMap(boolean useTileSet0, boolean useMap1) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void setTileSetManager(TileSetManager manager) {
        // TODO Auto-generated method stub
        this.tileSetManager = manager;
    }


    @Override
    public void loadWindow(boolean useTileSet0, boolean useMap1) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void loadPallettes() {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void setLYCompare(int lyCompare) {
        // TODO Auto-generated method stub
        this.LYCompare = lyCompare;
    }


    @Override
    public void setMMU(MMU mmu) {
        // TODO Auto-generated method stub
        this.mem = mmu;
    }


    @Override
    public void setGBS(GameBoyScreen gbs) {
        // TODO Auto-generated method stub
        this.gbs = gbs;
    }
    
    
    
}
