/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.plugin.notification.uengine.service;

import org.apache.ibatis.session.SqlSession;
import org.killbill.billing.plugin.notification.uengine.model.*;
import org.killbill.billing.plugin.notification.uengine.model.catalog.Plan;
import org.killbill.billing.plugin.notification.uengine.mybatis.InvoicePluginConnectionFactory;
import org.killbill.billing.plugin.notification.uengine.repository.InvoiceExtRepository;
import org.killbill.billing.plugin.notification.uengine.util.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by uengine on 2017. 2. 6..
 */
public class InvoiceExtService {

    public SqlSession getSqlSessionFactory() {
        return InvoicePluginConnectionFactory.getSqlSessionFactory()
                .openSession(true);
    }

    public NotificationConfig selectConfigByOrgId(String organization_id) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            return mapper.selectConfigByOrgId(organization_id);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public List<Template> selectByOrgIdAndType(String organization_id, String notification_type) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            Map map = new HashMap();
            map.put("organization_id", organization_id);
            map.put("notification_type", notification_type);
            return mapper.selectByOrgIdAndType(map);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public Organization selectOrganizationFromAccountId(String account_id) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            return mapper.selectOrganizationFromAccountId(account_id);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }


    public List<ProductVersion> selectVersionByProductId(String product_id) {
        SqlSession sessionFactory = getSqlSessionFactory();
        List<ProductVersion> productVersions = new ArrayList<ProductVersion>();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            List<ProductDaoVersion> productDaoVersions = mapper.selectVersionByProductId(product_id);

            for (ProductDaoVersion productDaoVersion : productDaoVersions) {
                List<Plan> plans = JsonUtils.unmarshalToList(productDaoVersion.getPlans());
                Map<String, Object> map = JsonUtils.convertClassToMap(productDaoVersion);
                map.put("plans", plans);
                productVersions.add(JsonUtils.convertValue(map, ProductVersion.class));
            }
            return productVersions;

        } catch (Exception ex) {
            ex.printStackTrace();
            return productVersions;
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public Product selectProductById(String id) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            ProductDao productDao = mapper.selectProductById(id);

            Map map = JsonUtils.convertValue(productDao, Map.class);
            if (map.get("vendors") != null) {
                map.put("vendors", JsonUtils.unmarshalToList(map.get("vendors").toString()));
            }
            Product product = JsonUtils.convertValue(map, Product.class);
            return product;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public ProductDistributionHistory insertHistory(ProductDistributionHistory history) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            mapper.insertHistory(history);

            return history;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public List<ProductDistributionHistory> selectHistoryByItemId(String invoice_item_id) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            return mapper.selectHistoryByItemId(invoice_item_id);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public List<OneTimeBuy> selectBundlePendingBuys(String bundle_id, String billing_date) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            Map map = new HashMap();
            map.put("bundle_id", bundle_id);
            map.put("billing_date", billing_date);
            return mapper.selectBundlePendingBuys(map);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public int updateOneTimeBuy(OneTimeBuy oneTimeBuy) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            return mapper.updateOneTimeBuy(oneTimeBuy);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public OneTimeBuy selectOneTimeBuyByItemId(String invoice_item_id) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            return mapper.selectOneTimeBuyByItemId(invoice_item_id);
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }

    public ProductVersion selectProductVersionByVersion(String product_id, Long version) {
        SqlSession sessionFactory = getSqlSessionFactory();
        try {
            InvoiceExtRepository mapper = sessionFactory.getMapper(InvoiceExtRepository.class);
            Map params = new HashMap();
            params.put("product_id", product_id);
            params.put("version", version);
            ProductDaoVersion productDaoVersion = mapper.selectProductVersionByVersion(params);

            List<Plan> plans = JsonUtils.unmarshalToList(productDaoVersion.getPlans());
            Map<String, Object> map = JsonUtils.convertClassToMap(productDaoVersion);
            map.put("plans", plans);
            ProductVersion productVersion = JsonUtils.convertValue(map, ProductVersion.class);
            return productVersion;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        }
    }
}
