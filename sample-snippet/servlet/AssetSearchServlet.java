package com.sample.servlets;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component(service = Servlet.class, property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.paths=" + "/bin/asset-search"
})
public class AssetSearchServlet extends SlingAllMethodsServlet {

    @Reference
    private QueryBuilder queryBuilder;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        String searchTerm = request.getParameter("query");
        String mimeType = request.getParameter("mimeType");
        String pageNumberParam = request.getParameter("pageNumber");
        String resultsPerPageParam = request.getParameter("resultsPerPage");

        int pageNumber = StringUtils.isNotBlank(pageNumberParam) ? Integer.parseInt(pageNumberParam) : 1;
        int resultsPerPage = StringUtils.isNotBlank(resultsPerPageParam) ? Integer.parseInt(resultsPerPageParam) : 10;

        Map<String, String> queryMap = new HashMap<>();

        queryMap.put("path", "/content/dam");
        queryMap.put("type", "dam:Asset");
        queryMap.put("orderby", "@jcr:content/jcr:lastModified");
        queryMap.put("orderby.sort", "desc");

        if (StringUtils.isNotBlank(searchTerm)) {
            queryMap.put("fulltext", searchTerm);
        }

        if (StringUtils.isNotBlank(mimeType)) {
            queryMap.put("property.name", "jcr:content/metadata/dc:format");
            queryMap.put("property.value", mimeType);
        }

        queryMap.put("p.offset", String.valueOf((pageNumber - 1) * resultsPerPage));
        queryMap.put("p.limit", String.valueOf(resultsPerPage));
        queryMap.put("p.guessTotal", "true");

        PredicateGroup predicateGroup = PredicateGroup.create(queryMap);
        Session jcrSession = request.getResourceResolver().adaptTo(Session.class);
        Query query = queryBuilder.createQuery(predicateGroup, jcrSession);
        response.setContentType("application/json");
        SearchResult searchResult = query.getResult();
        JSONObject jsonResponse;
		try {
			jsonResponse = buildJsonResponse(searchResult);
			response.getWriter().write(jsonResponse.toString());
		} catch (JSONException e) {
			
		}
        
    }

    private JSONObject buildJsonResponse(SearchResult searchResult) throws JSONException {
        JSONObject jsonResponse = new JSONObject();
        JSONArray assetsArray = new JSONArray();

        searchResult.getHits().forEach(hit -> {
            try {
                Resource assetResource = hit.getResource();
                Asset asset = assetResource.adaptTo(Asset.class);
                Resource assetMetadataResource = assetResource.getChild("jcr:content/metadata");
                ValueMap metadata = assetMetadataResource.getValueMap();

                JSONObject assetJson = new JSONObject();
                assetJson.put("title", metadata.get("dc:title", String.class));
                assetJson.put("description", metadata.get("dc:description", String.class));
                assetJson.put("caption", metadata.get("dc:caption", String.class));
                assetJson.put("altText", metadata.get("dc:altText", String.class));
                assetJson.put("size", asset.getOriginal().getSize());
                assetJson.put("duration", metadata.get("dc:duration", String.class));
                assetJson.put("tiffLength", metadata.get("dc:tiffLength", String.class));
                assetJson.put("assetPath", asset.getPath());
                
                JSONArray renditionsArray = new JSONArray();
                for (Rendition rendition : asset.getRenditions()) {
                    JSONObject renditionJson = new JSONObject();
                    renditionJson.put("renditionName", rendition.getName());
                    renditionJson.put("renditionPath", rendition.getPath());
                    renditionsArray.put(renditionJson);
                }
                assetJson.put("renditions", renditionsArray);

                assetsArray.put(assetJson);
            } catch (JSONException|RepositoryException e) {
               
            
			}
        });

        jsonResponse.put("totalResults", searchResult.getTotalMatches());
        jsonResponse.put("startIndex", searchResult.getStartIndex());
        jsonResponse.put("assets", assetsArray);

        return jsonResponse;
    }

}
