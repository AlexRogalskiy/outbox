package com.github.mhewedy.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OutboxEntity implements RowMapper<OutboxEntity> {

    private static final String PARM_TYPES_SEP = ",";
    private static final String PARAM_VALUES_SEP = "__,,__";

    public String id;
    public String serviceClass;
    public String methodName;
    public String paramTypes;
    public String paramValues;
    public String lockId;
    public Status status;
    public String errorMessage;
    public Instant createdDate;
    public Instant modifiedDate;

    public static OutboxEntity create(ObjectMapper objectMapper, Method method, List<Object> args) {
        var entity = new OutboxEntity();
        entity.serviceClass = method.getDeclaringClass().getName();
        entity.methodName = method.getName();
        entity.paramTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getName).collect(Collectors.joining(PARM_TYPES_SEP));
        entity.paramValues = args.stream()
                .map(it -> writeValueAsString(objectMapper, it)).collect(Collectors.joining(PARAM_VALUES_SEP));
        entity.createdDate = Instant.now();
        return entity;
    }

    public Class<?> getServiceClass() {
        return forName(this.serviceClass);
    }

    @SneakyThrows
    public Method getMethod() {
        Class<?>[] paramTypes = this.parseParamTypes();
        return getServiceClass().getDeclaredMethod(this.methodName, paramTypes);
    }

    @SneakyThrows
    public Object[] parseParamValues(ObjectMapper objectMapper) {
        Class<?>[] paramTypes = this.parseParamTypes();
        String[] paramValues = this.paramValues.split(PARAM_VALUES_SEP);
        Object[] objects = new Object[paramValues.length];

        for (int i = 0; i < paramValues.length; i++) {
            objects[i] = objectMapper.readValue(paramValues[i], paramTypes[i]);
        }
        return objects;
    }

    @SneakyThrows
    private static String writeValueAsString(ObjectMapper objectMapper, Object o) {
        return objectMapper.writeValueAsString(o);
    }

    private Class<?>[] parseParamTypes() {
        return Arrays.stream(this.paramTypes.split(PARM_TYPES_SEP))
                .map(this::forName)
                .toArray(Class<?>[]::new);
    }

    @SneakyThrows
    private Class<?> forName(String className) {
        return Class.forName(className);
    }

    @Override
    public OutboxEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        var entity = new OutboxEntity();
        entity.id = rs.getString("id");
        entity.serviceClass = rs.getString("service_class");
        entity.methodName = rs.getString("method_name");
        entity.paramTypes = rs.getString("param_types");
        entity.paramValues = rs.getString("param_values");
        entity.lockId = rs.getString("lock_id");
        entity.status = Status.values()[rs.getInt("status")];
        entity.errorMessage = rs.getString("error_message");
        return entity;
    }

    public Integer getStatusOrdinal() {
        return status == null ? null : status.ordinal();
    }

    public enum Status {
        LOCKED,
        SUCCESS,
        FAIL
    }
}
