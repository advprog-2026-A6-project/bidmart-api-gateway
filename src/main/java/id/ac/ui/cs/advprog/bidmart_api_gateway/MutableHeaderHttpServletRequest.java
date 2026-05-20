package id.ac.ui.cs.advprog.bidmart_api_gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MutableHeaderHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String> customHeaders = new LinkedHashMap<>();

    public MutableHeaderHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    public void putHeader(String name, String value) {
        if (value != null) {
            customHeaders.put(name, value);
        }
    }

    @Override
    public String getHeader(String name) {
        String customValue = customHeaders.get(name);
        return customValue != null ? customValue : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> values = new ArrayList<>();
        String customValue = customHeaders.get(name);
        if (customValue != null) {
            values.add(customValue);
        }
        Enumeration<String> originalHeaders = super.getHeaders(name);
        while (originalHeaders.hasMoreElements()) {
            values.add(originalHeaders.nextElement());
        }
        return Collections.enumeration(values);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = Collections.list(super.getHeaderNames());
        for (String customHeaderName : customHeaders.keySet()) {
            if (!names.contains(customHeaderName)) {
                names.add(customHeaderName);
            }
        }
        return Collections.enumeration(names);
    }
}
