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

    private static String API_URL = "api.wunderground.com/api/";
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
        if (splitPath.length == 0) {
            r.addAll(ROOT_NODES);
            return r;
        } else{
            return Collections.emptyList();
        }
    }

    @Override
    public ExternalData getItemByIdentifier(String s) throws ItemNotFoundException {
        return null;
    }

    @Override
    public ExternalData getItemByPath(String s) throws PathNotFoundException {
        return null;
    }

    @Override
    public Set<String> getSupportedNodeTypes() {
        return null;
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
        JSONArray tmdbResult = null;
        String year;
        String month;

        Map<String, Value> m = QueryHelper.getSimpleOrConstraints(externalQuery.getConstraint());
        if (m.containsKey("jcr:title")) {
            try {
                tmdbResult = queryWeather("query", m.get("jcr:title").getString()).getJSONArray("results");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (tmdbResult != null) {
            for (int i = 0; i < tmdbResult.length(); i++) {
                final String path = "test";
                if (path != null) {
                    results.add(path);
                }
            }
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
            HttpURL url = new HttpURL(API_URL, 80, path);

            Map<String, String> m = new LinkedHashMap<String, String>();
            for (int i = 0; i < params.length; i += 2) {
                m.put(params[i], params[i + 1]);
            }
            m.put(API_KEY, apiKeyValue);

            url.setQuery(m.keySet().toArray(new String[m.size()]), m.values().toArray(new String[m.size()]));
            long l = System.currentTimeMillis();
            System.out.println("Start request : " + url);
            GetMethod httpMethod = new GetMethod(url.toString());
            try {
                httpClient.getParams().setSoTimeout(10000);
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