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

package org.killbill.billing.plugin.notification.uengine.repository;

import org.killbill.billing.plugin.notification.uengine.model.*;

import java.util.List;
import java.util.Map;

/**
 * @author Seungpil PARK
 */
public interface InvoiceExtRepository {

    String NAMESPACE = InvoiceExtRepository.class.getName();

    NotificationConfig selectConfigByOrgId(String organization_id);

    Organization selectOrganizationFromAccountId(String account_id);

    List<Template> selectByOrgIdAndType(Map map);

    List<ProductDaoVersion> selectVersionByProductId(String product_id);

    ProductDao selectProductById(String id);

    int insertHistory(ProductDistributionHistory history);

    List<ProductDistributionHistory> selectHistoryByItemId(String invoice_item_id);

    List<OneTimeBuy> selectBundlePendingBuys(Map map);

    int updateOneTimeBuy(OneTimeBuy oneTimeBuy);

    OneTimeBuy selectOneTimeBuyByItemId(String invoice_item_id);

    ProductDaoVersion selectProductVersionByVersion(Map map);
}
