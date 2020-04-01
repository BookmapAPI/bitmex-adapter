package com.bookmap.plugins.layer0.bitmex.panels;

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
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.http.Header;

import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils;
import com.bookmap.plugins.layer0.bitmex.adapter.Constants;
import com.bookmap.plugins.layer0.bitmex.adapter.LogBitmex;
import com.bookmap.plugins.layer0.bitmex.messages.ModuleTargetedHttpRequestFeedbackMessage;
import com.bookmap.plugins.layer0.bitmex.messages.ModuleTargetedLeverageMessage;
import com.bookmap.plugins.layer0.bitmex.messages.ProviderTargetedLeverageMessage;
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
import velox.api.layer1.messages.Layer1ApiUserInterModuleMessage;
import velox.api.layer1.messages.UserMessageLayersChainCreatedTargeted;
import velox.api.layer1.messages.indicators.AliasFilter;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyScreenSpacePainter;
import velox.api.layer1.messages.indicators.SettingsAccess;
import velox.api.layer1.settings.Layer1ConfigSettingsInterface;
import velox.api.layer1.settings.StrategySettingsVersion;
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
, Layer1ConfigSettingsInterface

{
    public static final String rateLimitName = "rateLimit";

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

    @StrategySettingsVersion(currentVersion = 1, compatibleVersions = {})
    private static class PanelSettings{
        public Map<String, Object> settings;

        public Object getSettingsUnit(String name) {
            if (settings == null) {
                settings = new HashMap<>();
            }
            return settings.get(name);
        }

        public void addSettings(String name, Object value) {
            this.settings.put(name, value);
        }
    }

    private SettingsAccess settingsAccess;
    private Map<String, PanelSettings> settingsMap = new HashMap<>();
    private Object lock = new Object();

    private static final String INDICATOR_NAME = "Leverage";
    private static final String notConnectedMessage = "not connected to adapter";
    private static final Gson gson = new Gson();
    private int rateLimit;
    private int rateLimitRemaining;
    private int timeOut;
    
    private Object objectLock = new Object();
    private final String targetPattern = "Leverage: ";
    private final String rateLimitPattern = "RateLimit left: ";
    private final String rateLimitJoinPattern = " of ";
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
    private Set<String> symbolsToRequestLeverage = new HashSet<>();
    private String currentAlias;
    private ScheduledExecutorService oneSecondTimer;

    
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
                int i = 0;

                while (i < 10 && (maxLeverages.get(symbol) == null || leverages.get(symbol) == null)) {
                    try {
                        Thread.sleep(1000);
                        i++;
                    } catch (InterruptedException e1) {
                        //
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

        if (oneSecondTimer != null) {
            oneSecondTimer.shutdownNow();
        }

        synchronized (indicatorsFullNameToUserName) {
            for (String userName: indicatorsFullNameToUserName.values()) {
                Layer1ApiUserMessageModifyScreenSpacePainter message = Layer1ApiUserMessageModifyScreenSpacePainter
                        .builder(BitmexPanel.class, userName).setIsAdd(false).build();
                provider.sendUserMessage(message);
            }
        }
    }
    
    private Color getLabelColor(int value, int maxValue) {
        if (maxValue <= 1) {
            return Color.GRAY;
        }
        if (value*100/maxValue <= 5) {
            return Color.GREEN;
        } else if ((value*100/maxValue <= 25)) {
            return Color.ORANGE;
        } else {
            return Color.RED;
        }
    }
    
    private Color getRateLimitColor(int value, int maxValue) {
        if (maxValue <= 1) {
            return Color.GRAY;
        } 
        if (value*100/maxValue >= 50) {
            return Color.GREEN;
        } else if ((value*100/maxValue >= 15)) {
            return Color.ORANGE;
        } else {
            return Color.RED;
        }
    }
        

    private void addLeverageSettings(final CustomSettingsPanel panel, String alias) {
        String symbol = alias.split("@")[0];
        Integer maxValue = maxLeverages.get(symbol);

        if (maxValue == null) return;
        
        if (maxValue == 1) {
            panel.setEnabled(false);
        } else {
            int value = leverages.get(symbol);
            JSlider slider = new JSlider(0, maxValue, (int) value);
            Hashtable<Integer, JLabel> labels = new Hashtable<>();
            int surplus = maxValue / 5;

            for (int i = 0; i <= maxValue; i += surplus) {
                JLabel label = new JLabel(String.valueOf(i));
                label.setForeground(Color.WHITE);
                labels.put(i, label);
            }
            slider.setPaintLabels(true);
            slider.setLabelTable(labels);

            JLabel statusLabel = new JLabel("", JLabel.LEFT);
            statusLabel.setForeground(getLabelColor(value, maxValue));
            statusLabel.setText(String.valueOf(value));

            slider.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    if (slider.getValueIsAdjusting()) {
                        int value = (int) ((JSlider) e.getSource()).getValue();
                        statusLabel.setForeground(getLabelColor(value, maxValue));
                        statusLabel.setText(String.valueOf(value));
                    } else {
                        int leverage = (int) ((JSlider) e.getSource()).getValue();
                        Integer actualLeverage = leverages.get(symbol);

                        if (actualLeverage == null || actualLeverage != leverage) {
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

            JCheckBox checkBox = new JCheckBox();
            boolean isShowRateLimit = getShowRateLimit(alias);
            checkBox.setSelected(isShowRateLimit);
            checkBox.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    boolean isShowRateLimit = checkBox.isSelected();
                    PanelSettings settings = getSettingsFor(alias);
                    settings.addSettings(rateLimitName, isShowRateLimit);
                    settingsChanged(currentAlias, settings);
                    ScreenSpacePainterAdapterExternal painter = painters.get(symbol);
                    if (painter != null) {
                        painter.setNeedUpdate(true);
                    }
                }
            });

            statusLabels.put(symbol, statusLabel);
            panels.put(symbol, panel);
            panel.addSettingsItem("Leverage:", slider);
            panel.addSettingsItem("Value:", statusLabel);
            panel.addSettingsItem("Show RateLimit:", checkBox);
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
               addIndicator();
           }
       } else if (data instanceof Layer1ApiUserInterModuleMessage) {
           String message = null;

           try (ClassLoaderObjectInputStream str = new ClassLoaderObjectInputStream(getClass().getClassLoader(),
                    new ByteArrayInputStream(SerializationUtils.serialize((Serializable) data)))) {
               data = str.readObject();

               if (data instanceof ModuleTargetedLeverageMessage) {
                   ModuleTargetedLeverageMessage ptm = (ModuleTargetedLeverageMessage) data;
                   message = ptm.getMessage();
                   acceptMessage(message);
               } else if (data instanceof ModuleTargetedHttpRequestFeedbackMessage) {
                   ModuleTargetedHttpRequestFeedbackMessage feedbackMessage = (ModuleTargetedHttpRequestFeedbackMessage) data;
                   Header[] headers = feedbackMessage.headers;

                   Header rateLimitHeader = ConnectorUtils.getHeader(headers, Constants.rateLimitHeaderName);
                   Header rateLimitRemainingHeader = ConnectorUtils.getHeader(headers, Constants.rateLimitRemainingHeaderName);

                   if (rateLimitHeader != null && rateLimitRemainingHeader != null) {
                       try {
                           rateLimit = Integer.parseInt(rateLimitHeader.getValue());
                           rateLimitRemaining = Integer.parseInt(rateLimitRemainingHeader.getValue());

                           String symbol = currentAlias.split("@")[0];
                           requestUpdateForSymbol(symbol);
                       } catch (Exception e) {
                           LogBitmex.infoClassOf(ConnectorUtils.class, " no ratelimit data", e);
                       }
                   }

                   if (feedbackMessage.response.contains("error")) {
                        if (feedbackMessage.response.contains("The system is currently overloaded")) {
                            this.rateLimit = 0;
                        } else {
                            Integer timeOut = getTimeoutFromErrorMessage(feedbackMessage.response);
                            if (timeOut != null) {
                                this.timeOut = timeOut;
                            }
                        }
                   }
               }
            } catch (Exception e) {
                LogBitmex.info("", e);
            }
        }
   }

   @Override
   public ScreenSpacePainter createScreenSpacePainter(String indicatorName, String indicatorAlias,
           ScreenSpaceCanvasFactory screenSpaceCanvasFactory) {
       currentAlias = indicatorAlias;

       if (oneSecondTimer == null) {
           initializeExecutorWithTask();
       }
       
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
                       leverageColor = getLabelColor(leverage, maxLeverage);
                   }
                   text.addAttribute(TextAttribute.FOREGROUND, leverageColor, 10, textLength);
               }

               boolean isShowRateLimit = getShowRateLimit(currentAlias);

               if (isShowRateLimit) {
                    AttributedString rateLimitText = null;
                    String rateLimitTarget = null;
                    int beginIndex = rateLimitPattern.length();
                    Color rateLimitColor;
                    int endIndex = 0;
                    
                    if (rateLimit < 1) {
                        String unknownValue = "unknown";
                        rateLimitTarget = rateLimitPattern + unknownValue;
                        rateLimitText = new AttributedString(rateLimitTarget);
                        rateLimitColor = Color.ORANGE;
                        endIndex = rateLimitTarget.length();
                    } else {
                        rateLimitTarget = rateLimitPattern + String.valueOf(rateLimitRemaining)
                                + rateLimitJoinPattern + String.valueOf(rateLimit);
                        rateLimitText = new AttributedString(rateLimitTarget);
                        rateLimitColor = getRateLimitColor(rateLimitRemaining, rateLimit);
                        
                        endIndex = beginIndex + String.valueOf(rateLimit).length();
                    }
                    int rectangle1Length = (rateLimitPattern.length() + rateLimitJoinPattern.length() + 4) * 9;
                    g2d.fillRoundRect(1, 38, rectangle1Length, 36, 10, 10);
                    g2d.fillRoundRect(1, 1, Math.max(rectangleLength, rectangle1Length), 36, 10, 10);

                    rateLimitText.addAttribute(TextAttribute.FOREGROUND, Color.WHITE, 0, rateLimitTarget.length());
                    rateLimitText.addAttribute(TextAttribute.FOREGROUND, rateLimitColor, beginIndex, endIndex);
                    textLength = rateLimitTarget.length();
                    rateLimitText.addAttribute(TextAttribute.FONT, new Font("Arial", Font.BOLD, 14), 0, textLength);
                    g2d.drawString(rateLimitText.getIterator(), 24, 24 + 38);
                }
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

    public void sendToSocket(String message) {
        ProviderTargetedLeverageMessage providerMessage = new ProviderTargetedLeverageMessage();
        providerMessage.setMessage(message);
        provider.sendUserMessage(providerMessage);
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
        if (!isBitmex(alias))
            return;

        synchronized (objectLock) {
            symbolsToRequestLeverage.add(alias);
            activeAliases.add(alias);
        }

        requestLeverage();
        symbolsToRequestLeverage.clear();
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
            labelColor = getLabelColor(leverage, maxLeverage);
            
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
        
        requestUpdateForSymbol(symbol);
        return symbol;
    }
    
    private static boolean isBitmex(String alias) {
        String[] aliasSplitted = alias.split("@");
        return aliasSplitted != null && aliasSplitted.length != 0 && aliasSplitted[1].equals("MEX");
    }
    
    private void requestUpdateForSymbol(String symbol) {
        ScreenSpacePainterAdapterExternal painter = painters.get(symbol);
        if (painter != null) {
            painter.setNeedUpdate(true);
        }
    }
    
    static Integer getTimeoutFromErrorMessage(String errorMessage) {
        Pattern patcher = Pattern.compile("Rate limit exceeded, retry in \\d+ seconds.");
        Matcher matcher = patcher.matcher(errorMessage);

        if (matcher.find()) {
            String exp = matcher.group();
            patcher = Pattern.compile("\\d+");
            matcher = patcher.matcher(exp);

            if (matcher.find()) {
                String s = matcher.group();
                return Integer.valueOf(s);
            }
        }
        return null;
    }

    @Override
    public void acceptSettingsInterface(SettingsAccess arg0) {
        synchronized (lock) {
            this.settingsAccess = arg0;
        }
    }
    
    public boolean getShowRateLimit(String alias) {
        PanelSettings settings = getSettingsFor(alias);
        
        if (settings != null) {
            Boolean getShowRateLimit = (Boolean) settings.getSettingsUnit(rateLimitName);
            if (getShowRateLimit != null) return getShowRateLimit;
        }
        return true;
    }
    
    private PanelSettings getSettingsFor(String alias) {
        synchronized (lock) {
            PanelSettings settings = settingsMap.get(alias);
            if (settings == null) {
                settings = (PanelSettings) settingsAccess.getSettings(alias, getClass().getName(), PanelSettings.class);
                settingsMap.put(alias, settings);
            }
            return settings;
        }
    }
    
    protected void settingsChanged(String settingsAlias, PanelSettings settingsObject) {
        synchronized (lock) {
            settingsAccess.setSettings(settingsAlias, getClass().getName(), settingsObject, settingsObject.getClass());
        }
    }

    private void initializeExecutorWithTask() {
        
        oneSecondTimer = Executors.newSingleThreadScheduledExecutor();
        
        oneSecondTimer.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                if (rateLimitRemaining < rateLimit) {
                    if (timeOut > 0) {
                        timeOut--;
                    } else {
                        rateLimitRemaining++;
                    }
                    String symbol = currentAlias.split("@")[0];
                    requestUpdateForSymbol(symbol);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);

    }
}
