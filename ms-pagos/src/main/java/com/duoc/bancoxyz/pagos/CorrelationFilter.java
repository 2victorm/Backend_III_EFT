package com.duoc.bancoxyz.pagos;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.*;
@Component public class CorrelationFilter extends OncePerRequestFilter  {
    private static final Logger log=LoggerFactory.getLogger(CorrelationFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException  {
        String id=req.getHeader("X-Correlation-ID");
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,80}"))id=UUID.randomUUID().toString();
        MDC.put("correlationId",id);
        res.setHeader("X-Correlation-ID",id);
        try  {
            chain.doFilter(req,res);
        }
        finally  {
            log.info("{} {} status={} correlationId={}",req.getMethod(),req.getRequestURI(),res.getStatus(),id);
            MDC.remove("correlationId");
        }
    }
}
