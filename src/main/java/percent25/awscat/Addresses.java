package percent25.awscat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Splitter;
import com.google.gson.Gson;

// arn:aws:dynamo:us-east-1:102938475610:table/MyTable,c=1,delete=true,wcu=5
public class Addresses {
    /**
     * base
     * 
     * @param address e.g., "dynamo:MyTable,c=1,delete=true,wcu=5"
     * @return e.g., "dynamo:MyTable"
     */
    public static String base(String address) {

        // name,foo=1,bar=2
        // ns:name,foo=1,bar=2

        // dynamo:MyTable,c=1,delete=true,wcu=5
        // arn:aws:dynamo:us-east-1:102938475610:table/MyTable,c=1,delete=true,wcu=5

        int index = address.indexOf(",");
        if (index != -1)
            address = address.substring(0, index);
        return address;
    }

    // arn:aws:dynamo:us-east-1:102938475610:table/MyTable,c=1,delete=true,wcu=5
    public static <T> T options(String address, Class<T> classOfT) {
        Map<String, String> options = new HashMap<>();
        Iterator<String> iter = Splitter.on(",").trimResults().split(address).iterator();
        iter.next(); // skip address base
        while (iter.hasNext()) {
            Iterator<String> keyAndValue = Splitter.on("=").trimResults().split(iter.next()).iterator();
            String key = keyAndValue.next();
            String value = "true";
            if (keyAndValue.hasNext())
                value = keyAndValue.next();
            options.put(key, value);
        }
        return new Gson().fromJson(new Gson().toJson(options), classOfT);
    }

}
