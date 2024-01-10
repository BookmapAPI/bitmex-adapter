package com.bookmap.plugins.layer0.bitmex.adapter;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SpotSizeConverter {
    private final String SPOT_IDENTIFIER = "SPOT";
    private final Map<String, BmInstrument> aliasToSpotInstruments;

    public SpotSizeConverter(Set<BmInstrument> spotInstruments) {
        aliasToSpotInstruments = spotInstruments.stream()
                .filter(e -> e.getTyp().equals(Constants.typesToSpecifiers.get(SPOT_IDENTIFIER)))
                .collect(Collectors.toMap(e -> e.getSymbol(), e -> e));
    }

    public boolean isSpot(String alias){
        return aliasToSpotInstruments.containsKey(alias);
    }

    public int toSpotSize(String alias, int toSpotSize){
        double lotSize = aliasToSpotInstruments.get(alias).getLotSize();
        return (int)(toSpotSize * lotSize);
    }

    public int fromSpotSize(String alias, int toSpotSize){
        double lotSize = aliasToSpotInstruments.get(alias).getLotSize();
        return (int)(Math.round(toSpotSize / lotSize));
    }
}
