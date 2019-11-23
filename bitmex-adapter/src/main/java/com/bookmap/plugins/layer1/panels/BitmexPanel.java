package com.bookmap.plugins.layer1.panels;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.Socket;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.bookmap.plugins.layer0.bitmex.adapter.LogBitmex;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import velox.api.layer1.Layer1ApiAdminAdapter;
import velox.api.layer1.Layer1ApiFinishable;
import velox.api.layer1.Layer1ApiInstrumentAdapter;
import velox.api.layer1.Layer1ApiProvider;
import velox.api.layer1.Layer1CustomPanelsGetter;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1Attachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.annotations.Layer1TradingStrategy;
import velox.api.layer1.common.ListenableHelper;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpaceCanvas;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpaceCanvas.CanvasIcon;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpaceCanvas.CompositeCoordinateBase;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpaceCanvas.CompositeHorizontalCoordinate;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpaceCanvas.CompositeVerticalCoordinate;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpaceCanvas.PreparedImage;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpaceCanvasFactory;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpaceCanvasFactory.ScreenSpaceCanvasType;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpacePainter;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpacePainterAdapter;
import velox.api.layer1.layers.strategies.interfaces.ScreenSpacePainterFactory;
import velox.api.layer1.messages.UserMessageLayersChainCreatedTargeted;
import velox.api.layer1.messages.indicators.AliasFilter;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyScreenSpacePainter;
import velox.gui.StrategyPanel;

@Layer1Attachable
@Layer1TradingStrategy
@Layer1StrategyName("BitMEX Panel")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class BitmexPanel implements Layer1ApiFinishable
, Layer1CustomPanelsGetter
, Layer1ApiAdminAdapter
, Layer1ApiInstrumentAdapter
, ScreenSpacePainterFactory

{
    private static interface ScreenSpacePainterAdapterExternal extends ScreenSpacePainterAdapter{
        void setNeedUpdate(boolean needUpdate);
    }
    
    private static class CustomSettingsPanel extends StrategyPanel {
        private static final long serialVersionUID = -2829435001075847741L;
        private static final int xgap = 10;
        private static final int ygap = 15;
        private int row = 0;

        public CustomSettingsPanel(String title) {
            super(title);
            GridBagLayout layout = new GridBagLayout();
            layout.columnWidths = new int[] {xgap, 0, xgap, 0, xgap};
            layout.columnWeights = new double[] {0, 1, 0, 1, 0};
            setLayout(layout);
        }

        public void addSettingsItem(String label, Component c) {
            
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = row++;
            constraints.gridwidth = 1;
            constraints.gridx = 1;
            constraints.insets = new Insets(0, 0, ygap, 0);
            constraints.fill = GridBagConstraints.HORIZONTAL;
            
            if (row == 1) {
                constraints.insets.top = ygap;
            }
            
            add(new JLabel(label), constraints);
            constraints.gridx = 3;
            add(c, constraints);
        }
    }
    
    private static class CustomAliasFilter implements  AliasFilter {
        @Override
        public boolean isDisplayedForAlias(String alias) {
            return BitmexPanel.isBitmex(alias);
        }  
    };
     
    private static final String INDICATOR_NAME = "Leverage";
    private static final String notConnectedMessage = "not connected to adapter";
    private static final Gson gson = new Gson();
    
    private Socket client;
    private BufferedReader br;
    private PrintWriter pw;
    private DataInputStream in;
    private Object threadLock = new Object();
    private Object objectLock = new Object();
    private Thread connectingThread;
    private AtomicBoolean isConnecting = new AtomicBoolean(true);
    private AtomicBoolean isEnabled= new AtomicBoolean(true);
    private final int socketPort = 9998;
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private final String targetPattern = "Leverage: ";
    private Map <String, ScreenSpacePainterAdapterExternal> painters = new ConcurrentHashMap<>();
    private Map <String, JLabel> statusLabels = new ConcurrentHashMap<>();
    private Map <String, CustomSettingsPanel> panels = new ConcurrentHashMap<>();
    private Map <String, Integer> leverages = new ConcurrentHashMap<>();
    private Map <String, Integer> maxLeverages = new ConcurrentHashMap<>();
    private Map <String, String> messages = new ConcurrentHashMap<>();
    private Set<String> activeAliases = new HashSet<>();
    private CustomAliasFilter filter = new CustomAliasFilter();
    private String latestMessage = "";
    private Layer1ApiProvider provider;
    private Map<String, String> indicatorsFullNameToUserName = new HashMap<>();
    private Map<String, String> indicatorsUserNameToFullName = new HashMap<>();
    Set<String> symbolsToRequestLeverage = new HashSet<>();
    
    public BitmexPanel(Layer1ApiProvider provider) {
        this.provider = provider;
        ListenableHelper.addListeners(provider, this);
    }

    @Override
    public StrategyPanel[] getCustomGuiFor(String alias, String indicatorName) {
        printIfChanged("get custom gui for " + alias);
        if (!isBitmex(alias)) return null;
        
        CustomSettingsPanel panel = new CustomSettingsPanel("Settings");
        boolean isActive;
        synchronized (objectLock) {
            isActive = activeAliases.contains(alias);
        }
        
        if (isActive) {
            printIfChanged("Active aliases do contain " + alias);

            Thread t = new Thread(() -> {
                printIfChanged("Panel thread launched");
                String symbol = alias.split("@")[0];

                printIfChanged("Panel thread: Checking Leverage or maxLeverage == null");

                while (maxLeverages.get(symbol) == null || leverages.get(symbol) == null) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                printIfChanged("Panel thread: passed to SwingUtilities");

                SwingUtilities.invokeLater(() -> {
                    printIfChanged("Panel thread: SwingUtilities invoked");
                    addLeverageSettings(panel, alias);
                });
            });
            t.start();
        } else {
            printIfChanged("Active aliases do not " + alias);
            return null;
        }
        
        printIfChanged("Strategy panel returned");
        return new StrategyPanel[] {panel};
    }

    @Override
    public void finish() {
        activeAliases.clear();
        for (CustomSettingsPanel panel : panels.values()) {
            panel.invalidate();
            panel.revalidate();
            panel.getParent().repaint();
        }
        isEnabled.set(false);
        closeSocket();

        synchronized (indicatorsFullNameToUserName) {
            for (String userName: indicatorsFullNameToUserName.values()) {
                Layer1ApiUserMessageModifyScreenSpacePainter message = Layer1ApiUserMessageModifyScreenSpacePainter
                        .builder(BitmexPanel.class, userName).setIsAdd(false).build();
                provider.sendUserMessage(message);
            }
        }
    }
    
    private Color setLabelColor(int value, int maxValue) {
        if (maxValue == 1) {
            return Color.GRAY;
        } if (value*100/maxValue <= 5) {
            return Color.GREEN;
        } else if ((value*100/maxValue <= 25)) {
            return Color.ORANGE;
        } else {
            return Color.RED;
        }
    }
        

    private void addLeverageSettings(final CustomSettingsPanel panel, String alias) {
        String symbol = alias.split("@")[0];

        int maxValue = maxLeverages.get(symbol);
        if (maxValue == 1) {
            panel.setEnabled(false);
        } else {
        int value = leverages.get(symbol);
        JSlider slider = new JSlider(0, maxValue, (int)value);
        

        Hashtable<Integer, JLabel> labels = new Hashtable<>();
        int surplus = maxValue/5;
        
        for (int i = 0; i <= maxValue; i+=surplus) {
            JLabel label = new JLabel(String.valueOf(i));
            label.setForeground(Color.WHITE);
            labels.put(i, label);
        }
        slider.setPaintLabels(true);
        slider.setLabelTable(labels);
        
        JLabel statusLabel = new JLabel("",JLabel.LEFT);
        statusLabel.setForeground(setLabelColor(value, maxValue));
        statusLabel.setText(String.valueOf(value));
        
        slider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (slider.getValueIsAdjusting()) {
                    int value = (int)((JSlider)e.getSource()).getValue();
                    statusLabel.setForeground(setLabelColor(value, maxValue));
                    statusLabel.setText(String.valueOf(value));
                } else {
                int leverage = (int) ((JSlider)e.getSource()).getValue();
                Integer actualLeverage = leverages.get(symbol); 

                if (actualLeverage == null || actualLeverage != leverage){
                    statusLabel.setText(String.valueOf(leverage));
                    statusLabel.setForeground(Color.GRAY);
                }
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                map.put("symbol", alias);
                map.put("leverage", leverage);
                gson.toJson(map);
                String message = gson.toJson(map);
                sendToSocket(message);
                }
            }
        });
        
        statusLabels.put(symbol, statusLabel);
        panels.put(symbol, panel);
        panel.addSettingsItem("Leverage:", slider);
        panel.addSettingsItem("Value:", statusLabel);
        panel.invalidate();
        panel.revalidate();
        panel.getParent().repaint();
        printIfChanged("Panel revalidated");
        }
    }

  
   public void addIndicator() {
       printIfChanged("Indicator added");
       Layer1ApiUserMessageModifyScreenSpacePainter message = getUserMessageAdd(INDICATOR_NAME);
       
       synchronized (indicatorsFullNameToUserName) {
           indicatorsFullNameToUserName.put(message.fullName, message.userName);
           indicatorsUserNameToFullName.put(message.userName, message.fullName);
       }
       provider.sendUserMessage(message);
   }
   
   private Layer1ApiUserMessageModifyScreenSpacePainter getUserMessageAdd(String userName) {
       return Layer1ApiUserMessageModifyScreenSpacePainter.builder(BitmexPanel.class, userName)
               .setIsAdd(true)
               .setAliasFilter(filter)
               .setScreenSpacePainterFactory(this)
               .build();
   }
   
   @Override
   public void onUserMessage(Object data) {
       if (data.getClass() == UserMessageLayersChainCreatedTargeted.class) {
           UserMessageLayersChainCreatedTargeted message = (UserMessageLayersChainCreatedTargeted) data;
           if (message.targetClass == getClass()) {
               printIfChanged("on user message");
               addIndicator();
               startOutputConnection();
           }
       }
   }

   @Override
   public ScreenSpacePainter createScreenSpacePainter(String indicatorName, String indicatorAlias,
           ScreenSpaceCanvasFactory screenSpaceCanvasFactory) {
       
       ScreenSpaceCanvas heatmapCanvas = screenSpaceCanvasFactory.createCanvas(ScreenSpaceCanvasType.HEATMAP);
       ScreenSpacePainterAdapterExternal painter = new ScreenSpacePainterAdapterExternal() {

           int heatmapFullPixelsWidth;
           int heatmapPixelsHeight;
           String target = targetPattern;
           boolean needToUpdateHeatmapImage = true;
           CanvasIcon heatmapIcon;
           
           @Override
           public void onHeatmapFullPixelsWidth(int heatmapFullPixelsWidth) {
               needToUpdateHeatmapImage = true;
               this.heatmapFullPixelsWidth = heatmapFullPixelsWidth;
           }
           
           @Override
           public void onHeatmapPixelsHeight(int heatmapPixelsHeight) {
               needToUpdateHeatmapImage = true;
               this.heatmapPixelsHeight = heatmapPixelsHeight;
           }

           @Override
           public void onMoveEnd() {
               if (needToUpdateHeatmapImage) {
                   PreparedImage icon = generateCrossedBoxIcon(
                           heatmapFullPixelsWidth, heatmapPixelsHeight);
                   
                   CompositeHorizontalCoordinate x2 = new CompositeHorizontalCoordinate(CompositeCoordinateBase.PIXEL_ZERO, heatmapFullPixelsWidth, 0);
                   CompositeVerticalCoordinate y2 = new CompositeVerticalCoordinate(CompositeCoordinateBase.PIXEL_ZERO, heatmapPixelsHeight, 0);
                   if (heatmapIcon == null) {
                       CompositeHorizontalCoordinate x1 = new CompositeHorizontalCoordinate(CompositeCoordinateBase.PIXEL_ZERO, 0, 0);
                       CompositeVerticalCoordinate y1 = new CompositeVerticalCoordinate(CompositeCoordinateBase.PIXEL_ZERO, 0, 0);
                       heatmapIcon = new CanvasIcon(icon, x1, y1, x2, y2);
                       heatmapCanvas.addShape(heatmapIcon);
                   } else {
                       heatmapIcon.setImage(icon);
                       heatmapIcon.setX2(x2);
                       heatmapIcon.setY2(y2);
                   }
                   needToUpdateHeatmapImage = false;
               }
           }

           private PreparedImage generateCrossedBoxIcon(int width, int height) {
               BufferedImage icon = new BufferedImage(width, height,
                       BufferedImage.TYPE_INT_ARGB);
               Graphics2D g2d = (Graphics2D) icon.getGraphics();
               
               g2d.setColor(Color.GRAY);
               g2d.setComposite(AlphaComposite.SrcOver.derive(0.85f));
               String alias = indicatorAlias.split("@")[0];
               Integer leverage = leverages.get(alias);
               Integer maxLeverage = maxLeverages.get(alias);
               
                if (leverage != null && maxLeverage != null) {
                    target = targetPattern + leverage;
                } else {
                    String message = messages.get(alias);
                    if (message == null) {
                        message = notConnectedMessage;
                    }
                    target = targetPattern + message;
                }
               
               AttributedString text = new AttributedString(target);
               int textLength = target.length();
               text.addAttribute(TextAttribute.FONT, new Font("Arial", Font.BOLD, 14), 0, textLength);
               text.addAttribute(TextAttribute.FOREGROUND, Color.WHITE, 0, 9);
               int rectangleLength = 144;
               
               if (textLength > 9) {
                   Color leverageColor;
                   if (leverage == null || maxLeverage == null) {
                       leverageColor = Color.RED;
                       rectangleLength = textLength*9;
                   } else {
                       leverageColor = setLabelColor(leverage, maxLeverage);
                   }
                   text.addAttribute(TextAttribute.FOREGROUND, leverageColor, 10, textLength);
               }
               g2d.fillRoundRect(1, 1, rectangleLength, 36, 10, 10);
               g2d.drawString(text.getIterator(), 24, 24);
               g2d.dispose();

               PreparedImage preparedImage = new PreparedImage(icon);
               return preparedImage;
           }
           
           @Override
           public void dispose() {
               heatmapCanvas.dispose();
           }

            @Override
            public void setNeedUpdate(boolean needUpdate) {
                needToUpdateHeatmapImage = true;
            }
       };
       
       String alias = indicatorAlias.split("@")[0];
       painters.put(alias, painter);
       return painter;
   }

    public boolean isConnected() {
        return isConnected.get();
    }

    public void setConnected(boolean isConnected) {
        boolean isChanged = this.isConnected.compareAndSet(!isConnected, isConnected);
        if (isChanged) {
            printIfChanged("isConnected changed from " + !isConnected + " to " + isConnected + ". Now isConnected = " + this.isConnected.get());
        } else {
            printIfChanged("isConnected not changed from " + !isConnected + " to " + isConnected + ". isConnected = " + this.isConnected.get());
        }
    }

    public void sendToSocket(String message) {
            if (isConnected.get()) {
                printIfChanged("TO SERVER" + message);
              pw.println(message);

            if (pw.checkError()) {
                printIfChanged("Server not accessible");
                closeSocket();
                isConnected.set(false);

                synchronized (threadLock) {
                    if (!isConnecting.get()) {
                        startOutputConnection();
                    }
                }
            }
        }
    }
    
    private void startOutputConnection() {
        isConnecting.set(true);
        
        connectingThread = new Thread(() -> {
            printIfChanged("start client thread");
            
            while (isEnabled.get() && !isConnected.get()) {
                try {
                    InetAddress addr = InetAddress.getByName("localhost");
                    client = new Socket(addr, socketPort);
                    DataOutputStream out = new DataOutputStream(client.getOutputStream());
                    in = new DataInputStream(client.getInputStream());
                    br = new BufferedReader(new InputStreamReader(in));
                    pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out)), true);
                    isConnected.set(true);
                    isConnecting.set(false);
                    printIfChanged("client connection enabled");

                    startReading();
                    break;
                } catch (Exception e) {
                    printIfChanged("no server " + this.hashCode());
                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
        connectingThread.setName("->com.bookmap.plugins.layer1.panels.BitmexPanel: client connecting thread");
        connectingThread.start();
        sendToSocket("PING");
    }
    
    private void startReading() {
        Thread readingThread = new Thread(() -> {
            printIfChanged(" start client reading thread");

            if (isConnected.get()) {
                synchronized (objectLock) {
                    if (!symbolsToRequestLeverage.isEmpty()) {
                        requestLeverage();
                        symbolsToRequestLeverage.clear();
                    }
                }
            }

            while (isEnabled.get() && isConnected.get()) {
                printIfChanged("client reading cycle");
                
                try {
                    printIfChanged("reading from server");
                    String message = br.readLine();
                    printIfChanged("from client " + message);
                    if (message != null) {
                        acceptMessage(message);
                    } else {
                        throw new IOException();
                    }
                    printIfChanged("has been read from server");
                } catch (IOException e) {
                    e.printStackTrace();
                    printIfChanged(" closing client...");
                    closeSocket();
                    printIfChanged(" client socket closed (reading thread)");
                    isConnected.set(false);
                    
                    synchronized (threadLock) {
                        if (!isConnecting.get()) {
                            startOutputConnection();
                        }
                    }
                    break;
                }
            }
            printIfChanged("stopped client reading thread");

        });
        readingThread.setName("->com.bookmap.plugins.layer1.panels.BitmexPanel: server reading thread");
        readingThread.start();
    }

    private void closeSocket() {
        try {
            if (client != null) client.close();
            printIfChanged(" client socket closed (reading thread)");
         } catch (IOException e) {
             e.printStackTrace();
         }
    }
    
    private void printIfChanged(String text) {
        if (!text.equals(latestMessage)) {
            latestMessage = text;
            LogBitmex.infoClassOf(this.getClass(), text);
        }
    }
    
    @Override
    public void onInstrumentAdded(String alias, InstrumentInfo instrumentInfo) {
        printIfChanged("symbolsToRequestLeverage added " + alias);
        if (!isBitmex(alias)) return;

        synchronized (objectLock) {
            symbolsToRequestLeverage.add(alias);
            activeAliases.add(alias);
        }
        
        if (isConnected.get()) {
            requestLeverage();
            symbolsToRequestLeverage.clear();
        }
        printIfChanged("Active alias added " + alias);
    }
    
    @Override
    public void onInstrumentRemoved(String alias) {
        printIfChanged("Active alias removed " + alias);
        if (!isBitmex(alias)) return;
        String symbol = alias.split("@")[0];

        synchronized (objectLock) {
            activeAliases.remove(symbol);
            leverages.remove(symbol);
            maxLeverages.remove(symbol);
            messages.remove(symbol);
        }
    }
    
    
    private void requestLeverage() {
        for (String alias : symbolsToRequestLeverage) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("symbol", alias);
            map.put("ping", "");
            gson.toJson(map);
            String message = gson.toJson(map);
            printIfChanged("symbolsToRequestLeverage to remove " + alias);

            printIfChanged("to server " + message);
                sendToSocket(message);
        }
    }
    
    private String acceptMessage(String message) {
        printIfChanged("from server " + message);

        Map<String, Object> map = new HashMap<>();
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        map = gson.fromJson(message, mapType);
        String symbol = ((String) map.get("symbol")).split("@")[0];
        
        Double leverageAsDouble = (Double)map.get("leverage");
        Double maxLeverageAsDouble = (Double) map.get("maxLeverage");
        
        //no-leverage instrument case
        boolean isLeverage = true;
        if (leverageAsDouble != null && maxLeverageAsDouble != null
                && leverageAsDouble.equals(0.0) && maxLeverageAsDouble.equals(1.0)) {
            leverageAsDouble = null;
            maxLeverageAsDouble = null;
            isLeverage = false;
        }

        Integer leverage;
        if (leverageAsDouble == null) {
            leverage = null;
        } else {
            leverage = (int) Math.round(leverageAsDouble);
            leverages.put(symbol, leverage);
        }

        Integer maxLeverage;
        if (maxLeverageAsDouble == null) {
            maxLeverage = null;
        } else {
            maxLeverage = (int) Math.round(maxLeverageAsDouble);
            maxLeverages.putIfAbsent(symbol, maxLeverage);
        }
        
        JLabel label = statusLabels.get(symbol);
        String text;
        Color labelColor;
        
        if (leverage != null && maxLeverage != null) {
            text = String.valueOf(leverage);
            labelColor = setLabelColor(leverage, maxLeverage);
            
            if (label != null) {
                SwingUtilities.invokeLater(() -> {
                    label.setText(text);
                    label.setForeground(labelColor);
                });
            }
        } else {
            String symbolMessage;
            if (!isLeverage) {
                symbolMessage = "no leverage for this symbol";
            } else if (map.keySet().contains("isCredentialsEmpty") && (boolean) map.get("isCredentialsEmpty")) {
                symbolMessage = "not supported (no credentials)";
            } else {
                symbolMessage = notConnectedMessage;
            }
            messages.put(symbol, symbolMessage);
        }
        
      ScreenSpacePainterAdapterExternal painter = painters.get(symbol);
      if (painter != null) {
          painter.setNeedUpdate(true);
      }
        return symbol;
    }
    
    private static boolean isBitmex(String alias) {
        String[] aliasSplitted = alias.split("@");
        return aliasSplitted != null && aliasSplitted.length != 0 && aliasSplitted[1].equals("MEX");
    }
    
}
