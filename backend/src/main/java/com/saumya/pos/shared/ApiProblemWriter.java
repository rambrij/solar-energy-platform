package com.saumya.pos.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ApiProblemWriter {
    private final ObjectMapper mapper;
    public ApiProblemWriter(ObjectMapper mapper){this.mapper=mapper;}

    public ProblemDetail problem(HttpServletRequest request,HttpStatusCode status,String code,
            String title,String detail,Map<String,Object> properties){
        String safeCode=(code==null||!code.matches("[A-Z][A-Z0-9_]{1,63}"))?"REQUEST_REJECTED":code;
        ProblemDetail body=ProblemDetail.forStatusAndDetail(status,detail);
        body.setType(URI.create("urn:solar:problem:"+safeCode));
        body.setTitle(title);
        if(request!=null&&request.getRequestURI()!=null)body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("code",safeCode);
        if(properties!=null)properties.forEach(body::setProperty);
        return body;
    }

    public void write(HttpServletRequest request,HttpServletResponse response,HttpStatusCode status,
            String code,String title,String detail)throws IOException{
        String correlationId=UUID.randomUUID().toString();
        ProblemDetail body=problem(request,status,code,title,detail,Map.of("correlationId",correlationId));
        // Serialize before touching the servlet response so all headers are set before it is committed.
        byte[] payload=mapper.writeValueAsBytes(body);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Correlation-Id",correlationId);
        response.getOutputStream().write(payload);
    }
}
