package com.bitworksmc.headdb.core.command.sub;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HDBCommandSearchTest {

    @Test
    void combinesQuotedFilterValuesFromBukkitArguments() {
        assertEquals(
                List.of("category:Food & Drinks", "tags:dark red,wood", "--any"),
                HDBCommandSearch.combineQuotedArguments(new String[]{
                        "category:\"Food", "&", "Drinks\"", "tags:\"dark", "red\",wood", "--any"
                })
        );
    }
}
