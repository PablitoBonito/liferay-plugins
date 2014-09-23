/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.knowledgebase.display.portlet;

import com.liferay.knowledgebase.NoSuchArticleException;
import com.liferay.knowledgebase.NoSuchCommentException;
import com.liferay.knowledgebase.model.KBArticle;
import com.liferay.knowledgebase.model.KBArticleConstants;
import com.liferay.knowledgebase.portlet.BaseKBPortlet;
import com.liferay.knowledgebase.service.KBArticleLocalServiceUtil;
import com.liferay.knowledgebase.service.KBArticleServiceUtil;
import com.liferay.knowledgebase.service.permission.KBArticlePermission;
import com.liferay.knowledgebase.util.ActionKeys;
import com.liferay.knowledgebase.util.WebKeys;
import com.liferay.knowledgebase.util.comparator.KBArticlePriorityComparator;
import com.liferay.portal.NoSuchSubscriptionException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.security.auth.PrincipalException;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PortalUtil;

import java.io.IOException;

import java.util.List;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

/**
 * @author Peter Shin
 * @author Brian Wing Shun Chan
 * @author Sergio González
 */
public class DisplayPortlet extends BaseKBPortlet {

	@Override
	public void render(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		try {
			ThemeDisplay themeDisplay =
				(ThemeDisplay)renderRequest.getAttribute(WebKeys.THEME_DISPLAY);

			int status = getStatus(renderRequest);

			renderRequest.setAttribute(WebKeys.KNOWLEDGE_BASE_STATUS, status);

			KBArticle kbArticle = null;

			long resourcePrimKey = getResourcePrimKey(renderRequest);

			long parentResourcePrimKey = ParamUtil.getLong(
				renderRequest, "parentResourcePrimKey",
				KBArticleConstants.DEFAULT_PARENT_RESOURCE_PRIM_KEY);

			if (resourcePrimKey > 0) {
				kbArticle = KBArticleServiceUtil.getLatestKBArticle(
					resourcePrimKey, status);
			}
			else if (parentResourcePrimKey ==
						KBArticleConstants.DEFAULT_PARENT_RESOURCE_PRIM_KEY) {

				List<KBArticle> kbArticles =
					KBArticleLocalServiceUtil.getGroupKBArticles(
						themeDisplay.getScopeGroupId(), status, 0, 1,
						new KBArticlePriorityComparator(true));

				if (!kbArticles.isEmpty()) {
					kbArticle = kbArticles.get(0);
				}
			}

			renderRequest.setAttribute(
				WebKeys.KNOWLEDGE_BASE_KB_ARTICLE, kbArticle);
		}
		catch (Exception e) {
			if (e instanceof NoSuchArticleException ||
				e instanceof PrincipalException) {

				SessionErrors.add(renderRequest, e.getClass());

				SessionMessages.add(
					renderRequest,
					PortalUtil.getPortletId(renderRequest) +
						SessionMessages.KEY_SUFFIX_HIDE_DEFAULT_ERROR_MESSAGE);
			}
			else {
				throw new PortletException(e);
			}
		}

		super.render(renderRequest, renderResponse);
	}

	@Override
	protected void addSuccessMessage(
		ActionRequest actionRequest, ActionResponse actionResponse) {

		String actionName = ParamUtil.getString(
			actionRequest, ActionRequest.ACTION_NAME);

		if (actionName.equals("deleteKBArticle") ||
			actionName.equals("updateKBComment")) {

			return;
		}

		super.addSuccessMessage(actionRequest, actionResponse);
	}

	@Override
	protected String buildEditURL(
			ActionRequest actionRequest, ActionResponse actionResponse,
			KBArticle kbArticle)
		throws PortalException, SystemException {

		String namespace = actionResponse.getNamespace();
		String redirect = getRedirect(actionRequest, actionResponse);

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String editURL = PortalUtil.getLayoutFullURL(themeDisplay);

		editURL = HttpUtil.setParameter(
			editURL, "p_p_id",
			(String)actionRequest.getAttribute(WebKeys.PORTLET_ID));
		editURL = HttpUtil.setParameter(
			editURL, namespace + "mvcPath", templatePath + "edit_article.jsp");
		editURL = HttpUtil.setParameter(
			editURL, namespace + "redirect", redirect);
		editURL = HttpUtil.setParameter(
			editURL, namespace + "resourcePrimKey",
			kbArticle.getResourcePrimKey());
		editURL = HttpUtil.setParameter(
			editURL, namespace + "status", WorkflowConstants.STATUS_ANY);

		return editURL;
	}

	@Override
	protected void doDispatch(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		if (SessionErrors.contains(
				renderRequest, NoSuchArticleException.class.getName()) ||
			SessionErrors.contains(
				renderRequest, NoSuchCommentException.class.getName()) ||
			SessionErrors.contains(
				renderRequest, NoSuchSubscriptionException.class.getName()) ||
			SessionErrors.contains(
				renderRequest, PrincipalException.class.getName())) {

			include(templatePath + "error.jsp", renderRequest, renderResponse);
		}
		else {
			super.doDispatch(renderRequest, renderResponse);
		}
	}

	protected long getResourcePrimKey(RenderRequest renderRequest)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String urlTitle = ParamUtil.getString(renderRequest, "urlTitle");

		if (Validator.isNotNull(urlTitle)) {
			KBArticle kbArticle =
				KBArticleLocalServiceUtil.getKBArticleByUrlTitle(
					themeDisplay.getScopeGroupId(), urlTitle);

			return kbArticle.getResourcePrimKey();
		}

		PortletPreferences preferences = renderRequest.getPreferences();

		long defaultValue = GetterUtil.getLong(
			preferences.getValue("resourcePrimKey", null));

		String mvcPath = ParamUtil.getString(renderRequest, "mvcPath");

		if (((defaultValue == 0) && mvcPath.equals(viewTemplate)) ||
			mvcPath.equals("/display/select_configuration_article.jsp")) {

			return 0;
		}

		long resourcePrimKey = ParamUtil.getLong(
			renderRequest, "resourcePrimKey", defaultValue);

		if ((resourcePrimKey == 0) || (resourcePrimKey != defaultValue)) {
			return resourcePrimKey;
		}

		PermissionChecker permissionChecker =
			themeDisplay.getPermissionChecker();

		if (!KBArticlePermission.contains(
				permissionChecker, defaultValue, ActionKeys.VIEW)) {

			return 0;
		}

		return defaultValue;
	}

	protected int getStatus(RenderRequest renderRequest) throws Exception {
		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		if (!themeDisplay.isSignedIn()) {
			return WorkflowConstants.STATUS_APPROVED;
		}

		String value = renderRequest.getParameter("status");
		int status = GetterUtil.getInteger(value);

		if ((value != null) && (status == WorkflowConstants.STATUS_APPROVED)) {
			return WorkflowConstants.STATUS_APPROVED;
		}

		long resourcePrimKey = getResourcePrimKey(renderRequest);

		if (resourcePrimKey == 0) {
			return WorkflowConstants.STATUS_APPROVED;
		}

		PermissionChecker permissionChecker =
			themeDisplay.getPermissionChecker();

		if (KBArticlePermission.contains(
				permissionChecker, resourcePrimKey, ActionKeys.UPDATE)) {

			return ParamUtil.getInteger(
				renderRequest, "status", WorkflowConstants.STATUS_ANY);
		}

		return WorkflowConstants.STATUS_APPROVED;
	}

}