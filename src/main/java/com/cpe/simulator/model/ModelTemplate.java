package com.cpe.simulator.model;

import com.cpe.simulator.common.IdGenerator;

import java.util.Map;

public class ModelTemplate {

    public static class TemplateContext {
        private final String serialNumber;
        private final String oui;
        private final String productClass;
        private final String acsUrl;
        private final String connReqUrl;

        public TemplateContext(String serialNumber, String oui, String productClass,
                               String acsUrl, String connReqUrl) {
            this.serialNumber = serialNumber;
            this.oui = oui;
            this.productClass = productClass;
            this.acsUrl = acsUrl;
            this.connReqUrl = connReqUrl;
        }

        public String getSerialNumber() { return serialNumber; }
        public String getOui() { return oui; }
        public String getProductClass() { return productClass; }
        public String getAcsUrl() { return acsUrl; }
        public String getConnReqUrl() { return connReqUrl; }
    }

    public static String resolve(String template, TemplateContext ctx) {
        if (template == null || !template.contains("${")) {
            return template;
        }
        String result = template;
        result = result.replace("${random.uuid}", IdGenerator.uuid());
        result = result.replace("${device.serial}", ctx.getSerialNumber());
        result = result.replace("${device.oui}", ctx.getOui());
        result = result.replace("${device.productClass}", ctx.getProductClass());
        result = result.replace("${acs.url}", ctx.getAcsUrl());
        result = result.replace("${random.ip}", IdGenerator.randomIp());
        result = result.replace("${conn.req.url}", ctx.getConnReqUrl());
        return result;
    }

    public static DataModel resolveModel(DataModel model, TemplateContext ctx) {
        resolveNodeRecursive(model.getRoot(), ctx);
        return model;
    }

    private static void resolveNodeRecursive(ParameterNode node, TemplateContext ctx) {
        if (node.getValue() != null) {
            String original = node.getValue().getValue();
            String resolved = resolve(original, ctx);
            if (!resolved.equals(original)) {
                node.getValue().setValue(resolved);
            }
        }
        for (ParameterNode child : node.getChildren().values()) {
            resolveNodeRecursive(child, ctx);
        }
    }
}
