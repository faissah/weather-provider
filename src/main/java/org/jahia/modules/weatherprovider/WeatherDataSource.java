package org.jahia.modules.weatherprovider;

import com.google.common.collect.Sets;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpURL;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.ExternalData;
import org.jahia.modules.external.ExternalDataSource;
import org.jahia.modules.external.ExternalQuery;
import org.jahia.modules.external.query.QueryHelper;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jcr.*;
import java.util.*;

public class WeatherDataSource implements ExternalDataSource, ExternalDataSource.LazyProperty, ExternalDataSource.Searchable {

    public static final HashSet<String> ROOT_NODES = Sets.newHashSet("AL","AK","AZ","AR","CA","CO","CT","DE","FL","GA","HI","ID","IL","IN","IA","KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH","NJ","NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT","VT","VA","WA","WV","WI","WY");

    private static String API_URL = "api.wunderground.com";
    private static String API_CONDITIONS = "/conditions/q/";
    private static String API_KEY = "api_key";
    private String apiKeyValue;
    private EhCacheProvider ehCacheProvider;
    private Ehcache cache;

    public WeatherDataSource() {
        httpClient = new HttpClient();
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    private HttpClient httpClient;




    @Override
    public List<String> getChildren(String path) throws RepositoryException {
        List<String> r = new ArrayList<String>();

        String[] splitPath = path.split("/");
            try {
                    if (splitPath.length == 0) {
                        r.addAll(ROOT_NODES);
                        return r;
                    } else{
                        switch (splitPath.length){
                            case 2:
                                JSONObject o;
                                o = queryWeather("/api/"+apiKeyValue +"/conditions/q/"+ splitPath[1]+".json");
                                JSONObject response = o.getJSONObject("response");
                                JSONArray result = response.getJSONArray("results");

                                //for (int i = 0; i < result.length(); i++) {
                                //Limited the number of city per state at 3 since my dev key is pretty limited
                                for (int i = 0; i < 3; i++) {
                                        JSONObject city = result.getJSONObject(i);
                                    r.add(city.getString("name"));
                                }
                                return r;
                            case 3:
                                return r;

                        }
                    }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    @Override
    public ExternalData getItemByIdentifier(String identifier) throws ItemNotFoundException {
        String[] splitPath;
        try {
        if (identifier.equals("root")) {
            return new ExternalData(identifier, "/", "jnt:contentFolder", new HashMap<String, String[]>());
        }
        if (identifier.contains("state-")) {
            final String s = StringUtils.substringAfter(identifier, "state-");
            if (ROOT_NODES.contains(s)) {
                return new ExternalData(identifier, "/" + s, "jnt:contentFolder", new HashMap<String, String[]>());
            }
        }else if (identifier.contains("city-")) {
            final String citykey = StringUtils.substringAfter(identifier, "city-");
            splitPath = citykey.split("_");
            JSONObject response;
            response =  queryWeather("/api/" + apiKeyValue + "/conditions/q/" + splitPath[0] +"/"+splitPath[1] +".json");
            JSONObject city = response.getJSONObject("current_observation").getJSONObject("display_location");
            JSONObject city_observation = response.getJSONObject("current_observation");
            Map<String, String[]> properties = new HashMap<String, String[]>();
            if (city.getString("city") != null)
                properties.put("city", new String[]{city.getString("city")});
            if (city.getString("state") != null)
                properties.put("state", new String[]{city.getString("state")});
            if (city.getString("country") != null)
                properties.put("country", new String[]{city.getString("country")});
            if (city_observation.getString("temperature_string") != null)
                properties.put("temperature_string", new String[]{city_observation.getString("temperature_string")});
            if (city_observation.getString("feelslike_string") != null)
                properties.put("feelslike_string", new String[]{city_observation.getString("feelslike_string")});
            if (city_observation.getString("wind_string") != null)
                properties.put("wind_string", new String[]{city_observation.getString("wind_string")});
            if (city_observation.getString("precip_today_string") != null)
                properties.put("precip_today_string", new String[]{city_observation.getString("precip_today_string")});
            if (city_observation.getString("relative_humidity") != null)
                properties.put("relative_humidity", new String[]{city_observation.getString("relative_humidity")});
            return new ExternalData(identifier, getPathForCity(city), "jnt:city", properties);
        }
        return null;
        } catch (Exception e) {
            throw new ItemNotFoundException(e);
        }
    }

    @Override
    public ExternalData getItemByPath(String path) throws PathNotFoundException {
        String[] splitPath = path.split("/");
        try {
            if (path.endsWith("j:acl")) {
                throw new PathNotFoundException(path);
            }
            if (splitPath.length <= 1) {
                    return getItemByIdentifier("root");
            }else{
                switch (splitPath.length) {
                    case 2:
                        return getItemByIdentifier("state-"+ splitPath[1]);
                    case 3:
                        return getItemByIdentifier("city-" + splitPath[1]+"_"+ splitPath[2]);
                }
            }
        } catch (ItemNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Set<String> getSupportedNodeTypes() {
        return Sets.newHashSet("jnt:contentFolder", "jnt:state", "jnt:city");
    }

    @Override
    public boolean isSupportsHierarchicalIdentifiers() {
        return false;
    }

    @Override
    public boolean isSupportsUuid() {
        return false;
    }

    @Override
    public boolean itemExists(String s) {
        return false;
    }

    @Override
    public String[] getPropertyValues(String s, String s1) throws PathNotFoundException {
        return new String[0];
    }

    @Override
    public String[] getI18nPropertyValues(String s, String s1, String s2) throws PathNotFoundException {
        return new String[0];
    }

    @Override
    public Binary[] getBinaryPropertyValues(String s, String s1) throws PathNotFoundException {
        return new Binary[0];
    }

    @Override
    public List<String> search(ExternalQuery externalQuery) throws RepositoryException {
        List<String> results = new ArrayList<String>();
        String nodeType = QueryHelper.getNodeType(externalQuery.getSource());
        JSONObject o = null;
        JSONArray result= null;
        try {
            Map<String, Value> m = QueryHelper.getSimpleOrConstraints(externalQuery.getConstraint());
            if (m.containsKey("n")) {
                try {
                    o = queryWeather("/api/"+apiKeyValue +"/conditions/q/US/"+ m.get("n").getString()+".json").getJSONObject("response");
                    result = o.getJSONArray("results");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if (result != null) {
                for (int i = 0; i < result.length(); i++) {
                    final String path;
                        path = getPathForCity(result.getJSONObject(i));
                    if (path != null) {
                        results.add(path);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return results;
    }

    public void start() {
        try {
            if (!ehCacheProvider.getCacheManager().cacheExists("weather-cache")) {
                ehCacheProvider.getCacheManager().addCache("weather-cache");
            }
            cache = ehCacheProvider.getCacheManager().getCache("weather-cache");
        } catch (IllegalStateException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (CacheException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private JSONObject queryWeather(String path, String... params) throws RepositoryException {
        try {
            HttpURL url = new HttpURL(API_URL, -1, path);

            Map<String, String> m = new LinkedHashMap<String, String>();
            for (int i = 0; i < params.length; i += 2) {
                m.put(params[i], params[i + 1]);
            }

            url.setQuery(m.keySet().toArray(new String[m.size()]), m.values().toArray(new String[m.size()]));
            long l = System.currentTimeMillis();
            System.out.println("Start request : " + url);
            GetMethod httpMethod = new GetMethod(url.toString());
            try {
                httpClient.getParams().setSoTimeout(15000);
                httpClient.executeMethod(httpMethod);
                return new JSONObject(httpMethod.getResponseBodyAsString());
            } finally {
                httpMethod.releaseConnection();
                System.out.println("Request " + url + " done in "+(System.currentTimeMillis()-l) + "ms");
            }
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    private String getPathForCity(JSONObject city) throws JSONException {
        return "/" + StringUtils.substringBeforeLast(city.getString("state"), "-").replace("-", "/") + "/" + city.getString("city");
    }

    public void setApiKeyValue(String apiKeyValue) {
        this.apiKeyValue = apiKeyValue;
    }

    public String getApiKeyValue() {
        return apiKeyValue;
    }

    public void setCacheProvider(EhCacheProvider ehCacheProvider) {
        this.ehCacheProvider = ehCacheProvider;
    }

    public EhCacheProvider getCacheProvider() {
        return ehCacheProvider;
    }
}