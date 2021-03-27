package io.github.awscat;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.CaseFormat;
import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.springframework.boot.ApplicationArguments;

// arn:aws:dynamo:us-east-1:102938475610:table/MyTable,c=1,delete=true,wcu=5
public class Args {
    /**
     * parseArg
     * 
     * @param arg e.g., "dynamo:MyTable,c=1,delete=true,wcu=5"
     * @return e.g., "dynamo:MyTable"
     */
    public static String base(String arg) {

        // name,foo=1,bar=2
        // ns:name,foo=1,bar=2

        // dynamo:MyTable,c=1,delete=true,wcu=5
        // arn:aws:dynamo:us-east-1:102938475610:table/MyTable,c=1,delete=true,wcu=5

        int index = arg.indexOf(",");
        if (index != -1)
            arg = arg.substring(0, index);
        return arg;
    }

    // arn:aws:dynamo:us-east-1:102938475610:table/MyTable,c=1,delete=true,wcu=5
    public static <T> T options(String arg, Class<T> classOfT) {
        Map<String, String> options = new HashMap<String, String>();
        int index = arg.indexOf(",");
        if (index != -1) {
        options.putAll(Splitter.on(",").trimResults().withKeyValueSeparator("=").split(arg.substring(index+1)));
        }
        return new Gson().fromJson(new Gson().toJson(options), classOfT);
    }
  
    // public static String source(ApplicationArguments args) {
    //     return parseArg(args.getNonOptionArgs().get(0));
    // }

    // public static String target(ApplicationArguments args) {
    //     return parseArg(args.getNonOptionArgs().get(1));
    // }

    public static <T> T parseOptions(ApplicationArguments args, Class<T> classOfT) {
        JsonObject options = new JsonObject();
        for (String name : args.getOptionNames()) {
            String lowerCamel = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, name);
            options.addProperty(lowerCamel, true);
            for (String value : args.getOptionValues(name)) //###TODO doesn't handle multi-values
                options.addProperty(lowerCamel, value);
        }
        return new Gson().fromJson(options, classOfT);
    }
    
}
