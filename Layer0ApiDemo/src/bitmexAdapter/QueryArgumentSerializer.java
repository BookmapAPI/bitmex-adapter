package bitmexAdapter;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class QueryArgumentSerializer implements JsonSerializer<QueryArgument> {

		@Override
		public JsonElement serialize(QueryArgument arg, Type arg1, JsonSerializationContext arg2) {

			return new JsonPrimitive(
					new StringBuilder(arg.getTheme())
					.append(":")
					.append(arg.getSymbol())
					.toString()
			);
			
		}

	}