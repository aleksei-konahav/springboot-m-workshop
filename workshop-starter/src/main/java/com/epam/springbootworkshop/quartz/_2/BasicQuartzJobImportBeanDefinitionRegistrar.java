package com.epam.springbootworkshop.quartz._2;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;
import org.springframework.util.Assert;

public abstract class BasicQuartzJobImportBeanDefinitionRegistrar
        implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
    protected Environment environment;
    protected ResourceLoader resourceLoader;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Assert.notNull(environment, "Environment must not be null");
        Assert.notNull(resourceLoader, "ResourceLoader must not be null");

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RepeatableQuartzJob.class));

        Arrays.stream(getBasePackages(metadata))
                .map(scanner::findCandidateComponents)
                .flatMap(Collection::stream)
                .forEach(beanDefinition -> {
                    AbstractBeanDefinition jobDetailBeanDefinition =
                            BeanDefinitionBuilder.rootBeanDefinition(JobDetailFactoryBean.class)
                                    .addPropertyValue("name", beanDefinition.getBeanClassName() + "JobDetail")
                                    .addPropertyValue("durability", true)
                                    .addPropertyValue("requestsRecovery", false)
                                    .addPropertyValue("jobClass", beanDefinition.getBeanClassName())
                                    .getBeanDefinition();

                    final String jobDetailBeanName =
                            BeanDefinitionReaderUtils.registerWithGeneratedName(jobDetailBeanDefinition, registry);

                    Assert.isAssignable(AnnotatedBeanDefinition.class, beanDefinition.getClass());
                    AnnotatedBeanDefinition bd = (AnnotatedBeanDefinition) beanDefinition;
                    AnnotationAttributes jobAttrs =
                            Optional.ofNullable(bd.getMetadata().getAnnotationAttributes(RepeatableQuartzJob.class.getName()))
                                    .map(AnnotationAttributes::new)
                                    .orElseThrow(() -> new IllegalStateException(String.format("Unable to obtain "
                                            + "annotation attributes for %s!", RepeatableQuartzJob.class.getName())));

                    AbstractBeanDefinition jobTriggerBeanDefinition =
                            BeanDefinitionBuilder.rootBeanDefinition(SimpleTriggerFactoryBean.class)
                                    .addPropertyValue("name", beanDefinition.getBeanClassName() + "JobTrigger")
                                    .addPropertyValue("misfireInstruction", Trigger.MISFIRE_INSTRUCTION_SMART_POLICY)
                                    .addPropertyValue("repeatCount", -1)
                                    .addPropertyValue("repeatInterval", jobAttrs.getNumber("repeatInterval"))
                                    .addPropertyReference("jobDetail", jobDetailBeanName)
                                    .getBeanDefinition();

                    BeanDefinitionReaderUtils.registerWithGeneratedName(jobTriggerBeanDefinition, registry);
                });
    }

    protected abstract String[] getBasePackages(AnnotationMetadata metadata);

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
