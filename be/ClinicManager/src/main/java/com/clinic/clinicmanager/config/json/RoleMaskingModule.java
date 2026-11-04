package com.clinic.clinicmanager.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoleMaskingModule extends SimpleModule {

    public static final String HTTP_RESPONSE_ATTRIBUTE = RoleMaskingModule.class.getName() + ".httpResponse";

    public RoleMaskingModule() {
        super("RoleMaskingModule");
        setSerializerModifier(new RoleMaskingSerializerModifier());
    }

    private static class RoleMaskingSerializerModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                                                         List<BeanPropertyWriter> beanProperties) {
            for (int i = 0; i < beanProperties.size(); i++) {
                BeanPropertyWriter writer = beanProperties.get(i);
                RoleRestricted restricted = writer.getAnnotation(RoleRestricted.class);
                if (restricted != null) {
                    beanProperties.set(i, new RoleMaskedPropertyWriter(writer, restricted.value()));
                }
            }
            return beanProperties;
        }
    }

    private static class RoleMaskedPropertyWriter extends BeanPropertyWriter {

        private final String requiredRole;

        RoleMaskedPropertyWriter(BeanPropertyWriter base, String requiredRole) {
            super(base);
            this.requiredRole = requiredRole;
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            if (isMasked(prov)) {
                gen.writeFieldName(_name);
                gen.writeNull();
                return;
            }
            super.serializeAsField(bean, gen, prov);
        }

        @Override
        public void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            if (isMasked(prov)) {
                gen.writeNull();
                return;
            }
            super.serializeAsElement(bean, gen, prov);
        }

        private boolean isMasked(SerializerProvider prov) {
            return Boolean.TRUE.equals(prov.getAttribute(HTTP_RESPONSE_ATTRIBUTE))
                    && !SessionUtils.hasRole(requiredRole);
        }
    }
}
