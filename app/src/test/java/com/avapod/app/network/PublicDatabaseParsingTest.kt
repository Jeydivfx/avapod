package com.avapod.app.network

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PublicDatabaseParsingTest {
    @Test fun objectKeysRemainStable() {
        val children = JsonParser.parseString("""{"-podcast":{"title":"پادکست","is_featured":true},"deleted":null}""").databaseChildren()
        assertEquals(1, children.size)
        assertEquals("-podcast", children.single().first)
        assertEquals("پادکست", children.single().second.databaseString("title"))
        assertTrue(children.single().second.databaseBoolean("is_featured"))
        assertFalse(children.single().second.databaseBoolean("is_trending"))
    }

    @Test fun numericKeysAndNullGapsKeepTheirIds() {
        val children = JsonParser.parseString("""[null,{"name":"تاریخ"},null,{"name":"علم"}]""").databaseChildren()
        assertEquals(listOf("1", "3"), children.map { it.first })
    }

    @Test fun missingNodeIsEmpty() {
        assertTrue(JsonParser.parseString("null").databaseChildren().isEmpty())
        assertEquals("", JsonParser.parseString("{}").databaseString("description"))
    }

    @Test(expected = IOException::class) fun invalidCollectionFailsInsteadOfReplacingCache() {
        JsonParser.parseString("true").databaseChildren()
    }

    @Test(expected = IllegalStateException::class) fun malformedRecordFailsInsteadOfDroppingIt() {
        JsonParser.parseString("""{"good":{"title":"valid"},"bad":42}""").databaseChildren()
    }
}
