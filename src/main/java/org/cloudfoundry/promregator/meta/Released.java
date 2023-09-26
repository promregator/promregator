package org.cloudfoundry.promregator.meta;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * Indicates that an entity is released for usage of Promregator extensions.
 * The extension may assume that entities annotated with this annotation
 * are kept stable after upgrade from a contract perspective.
 * 
 * Entities not annotated (indirectly) with this annotation must be considered
 * an implementation detail of Promregator and may change without prior
 * notification.
 */
@Target({ TYPE, FIELD, METHOD, CONSTRUCTOR, PACKAGE })
@Documented
public @interface Released {
	String since();
}
