/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.framework.autoproxy;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving standard Spring Advisors from a BeanFactory,
 * for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AbstractAdvisorAutoProxyCreator
 */
public class BeanFactoryAdvisorRetrievalHelper {

	private static final Log logger = LogFactory.getLog(BeanFactoryAdvisorRetrievalHelper.class);

	private final ConfigurableListableBeanFactory beanFactory;

	@Nullable
	private volatile String[] cachedAdvisorBeanNames;


	/**
	 * Create a new BeanFactoryAdvisorRetrievalHelper for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAdvisorRetrievalHelper(ConfigurableListableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		this.beanFactory = beanFactory;
	}


	/**
	 * 这里获取到的一般都是硬编码注入的 Advisors,如xml中配置的，@bean导入的advisor
	 * Find all eligible Advisor beans in the current bean factory,
	 * ignoring FactoryBeans and excluding beans that are currently in creation.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> findAdvisorBeans() {
		// Determine list of advisor bean names, if not cached already.
		String[] advisorNames = this.cachedAdvisorBeanNames;
		if (advisorNames == null) {
			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the auto-proxy creator apply to them!
			// 获取BeanFactory中所有实现了Advisor.class的类名.
			// AspectJ方式实现的aop:org.springframework.aop.aspectj.annotation.
			// AnnotationAwareAspectJAutoProxyCreator.findCandidateAdvisors
			// )的方式多了AspectJ的获取:获取所有的Object.class，然后通过反射过滤有注解AspectJ的类
			advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
					this.beanFactory, Advisor.class, true, false);
			this.cachedAdvisorBeanNames = advisorNames;
		}
		if (advisorNames.length == 0) {
			return new ArrayList<>();
		}

		List<Advisor> advisors = new ArrayList<>();
		for (String name : advisorNames) {
			if (isEligibleBean(name)) {
				if (this.beanFactory.isCurrentlyInCreation(name)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Skipping currently created advisor '" + name + "'");
					}
				}
				else {
					try {
						// org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor: pointcut [AspectJExpressionPointcut: () execution(* spring.aop..*UserService.*(..))]; advice bean 'aopByCliassic2'
						/*
						aspectjpointcutadvisor:
						advice=org.springframework.aop.aspectj.AspectJMethodBeforeAdvice: advice method [public void spring.aop.AopByAspectjAndXml.beforeProcess()]; aspect name 'aopByAspectjAndXml'
						pc=org.springframework.aop.support.ComposablePointcut: AspectJExpressionPointcut: () execution(* spring.aop..MoneyServiceImpl.*(..)), org.springframework.aop.support.MethodMatchers$IntersectionIntroductionAwareMethodMatcher: org.springframework.aop.aspectj.AbstractAspectJAdvice$AdviceExcludingMethodMatcher: public void spring.aop.AopByAspectjAndXml.beforeProcess(), AspectJExpressionPointcut: () execution(* spring.aop..MoneyServiceImpl.*(..))
						 */
						advisors.add(this.beanFactory.getBean(name, Advisor.class));
					}
					catch (BeanCreationException ex) {
						Throwable rootCause = ex.getMostSpecificCause();
						if (rootCause instanceof BeanCurrentlyInCreationException) {
							BeanCreationException bce = (BeanCreationException) rootCause;
							String bceBeanName = bce.getBeanName();
							if (bceBeanName != null && this.beanFactory.isCurrentlyInCreation(bceBeanName)) {
								if (logger.isTraceEnabled()) {
									logger.trace("Skipping advisor '" + name +
											"' with dependency on currently created bean: " + ex.getMessage());
								}
								// Ignore: indicates a reference back to the bean we're trying to advise.
								// We want to find advisors other than the currently created bean itself.
								continue;
							}
						}
						throw ex;
					}
				}
			}
		}
		return advisors;
	}

	/**
	 * Determine whether the aspect bean with the given name is eligible.
	 * <p>The default implementation always returns {@code true}.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
