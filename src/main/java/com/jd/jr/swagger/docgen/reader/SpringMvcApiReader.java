package com.jd.jr.swagger.docgen.reader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;

import org.codehaus.plexus.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.jd.jr.swagger.docgen.GenerateException;
import com.jd.jr.swagger.docgen.LogAdapter;
import com.jd.jr.swagger.docgen.spring.SpringResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import io.swagger.converter.ModelConverters;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;

public class SpringMvcApiReader extends AbstractReader implements ClassSwaggerReader {
	private String resourcePath;
	private static final String SUCCESSFUL_OPERATION = "successful operation";

	public SpringMvcApiReader(Swagger swagger, LogAdapter log) {
		super(swagger, log);

	}

	@Override
	public Swagger read(Set<Class<?>> classes) throws GenerateException {
		// relate all methods to one base request mapping if multiple
		// controllers exist for that mapping
		// get all methods from each controller & find their request mapping
		// create map - resource string (after first slash) as key, new
		// SpringResource as value
		Map<String, SpringResource> resourceMap = generateResourceMap(classes);
		for (String str : resourceMap.keySet()) {
			SpringResource resource = resourceMap.get(str);
			read(resource);
		}

		return swagger;
	}

	public Swagger read(SpringResource resource) {
		if (swagger == null) {
			swagger = new Swagger();
		}
		String description;
		List<Method> methods = resource.getMethods();
		Map<String, Tag> tags = new HashMap<String, Tag>();

		List<SecurityRequirement> resourceSecurities = new ArrayList<SecurityRequirement>();

		// Add the description from the controller api
		Class<?> controller = resource.getControllerClass();
		RequestMapping controllerRM = controller.getAnnotation(RequestMapping.class);

		String[] controllerProduces = new String[0];
		String[] controllerConsumes = new String[0];
		if (controllerRM != null) {
			controllerConsumes = controllerRM.consumes();
			controllerProduces = controllerRM.produces();
		}

		if (controller != null && controller.isAnnotationPresent(Api.class)) {
			Api api = controller.getAnnotation(Api.class);
			if (!canReadApi(false, api)) {
				return swagger;
			}
			tags = updateTagsForApi(null, api);
			resourceSecurities = getSecurityRequirements(api);
			description = api.description();
		}

		resourcePath = resource.getControllerMapping();

		// collect api from method with @RequestMapping
		Map<String, List<Method>> apiMethodMap = collectApisByRequestMapping(methods);

		for (String path : apiMethodMap.keySet()) {
			for (Method method : apiMethodMap.get(path)) {

				RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
				if (requestMapping == null) {
					continue;
				}
				ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
				if (apiOperation == null || apiOperation.hidden()) {
					continue;
				}
				String httpMethod = null;

				Map<String, String> regexMap = new HashMap<String, String>();
				String operationPath = parseOperationPath(path, regexMap);

				// http method
				for (RequestMethod requestMethod : requestMapping.method()) {
					httpMethod = requestMethod.toString().toLowerCase();
					Operation operation = parseMethod(method);

					updateOperationParameters(new ArrayList<Parameter>(), regexMap, operation);

					updateOperationProtocols(apiOperation, operation);

					String[] apiProduces = requestMapping.produces();
					String[] apiConsumes = requestMapping.consumes();

					apiProduces = (apiProduces == null || apiProduces.length == 0) ? controllerProduces : apiProduces;
					apiConsumes = (apiConsumes == null || apiProduces.length == 0) ? controllerConsumes : apiConsumes;

					apiConsumes = updateOperationConsumes(new String[0], apiConsumes, operation);
					apiProduces = updateOperationProduces(new String[0], apiProduces, operation);

					updateTagsForOperation(operation, apiOperation);
					updateOperation(apiConsumes, apiProduces, tags, resourceSecurities, operation);
					updatePath(operationPath, httpMethod, operation);
				}
			}

		}
		return swagger;
	}

	private Operation parseMethod(Method method) {
		Operation operation = new Operation();

		ApiOperation apiOperation = (ApiOperation) method.getAnnotation(ApiOperation.class);
		ApiResponses responseAnnotation = method.getAnnotation(ApiResponses.class);

		String operationId = method.getName();
		String responseContainer = null;

		Type responseType = null;
		Map<String, Property> defaultResponseHeaders = new HashMap<String, Property>();

		if (apiOperation != null) {
			if (apiOperation.hidden())
				return null;
			if (!"".equals(apiOperation.nickname()))
				operationId = method.getName();

			defaultResponseHeaders = parseResponseHeaders(apiOperation.responseHeaders());

			operation.summary(apiOperation.value()).description(apiOperation.notes());

			if (apiOperation.response() != null && !isVoid(apiOperation.response()))
				responseType = apiOperation.response();
			if (!"".equals(apiOperation.responseContainer()))
				responseContainer = apiOperation.responseContainer();
			if (apiOperation.authorizations() != null) {
				List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
				for (Authorization auth : apiOperation.authorizations()) {
					if (auth.value() != null && !"".equals(auth.value())) {
						SecurityRequirement security = new SecurityRequirement();
						security.setName(auth.value());
						AuthorizationScope[] scopes = auth.scopes();
						for (AuthorizationScope scope : scopes) {
							if (scope.scope() != null && !"".equals(scope.scope())) {
								security.addScope(scope.scope());
							}
						}
						securities.add(security);
					}
				}
				if (securities.size() > 0) {
					for (SecurityRequirement sec : securities)
						operation.security(sec);
				}
			}
			if (apiOperation.consumes() != null && !apiOperation.consumes().isEmpty()) {
				operation.consumes(apiOperation.consumes());
			}
			if (apiOperation.produces() != null && !apiOperation.produces().isEmpty()) {
				operation.produces(apiOperation.produces());
			}
		}

		if (responseType == null) {
			// pick out response from method declaration
			LOG.info("picking up response class from method " + method);
			responseType = method.getGenericReturnType();
		}
		if (isValidResponse(responseType)) {
			int responseCode = 200;
			if (apiOperation != null) {
				responseCode = apiOperation.code();
			}
			if (isPrimitive(responseType)) {
				Property property = ModelConverters.getInstance().readAsProperty(responseType);
				if (property != null) {
					Property responseProperty = ContainerWrapper.wrapContainer(responseContainer, property);
					operation.response(responseCode, new Response().description(SUCCESSFUL_OPERATION)
							.schema(responseProperty).headers(defaultResponseHeaders));
				}
			} else {
				Map<String, Model> models = ModelConverters.getInstance().read(responseType);
				if (models.size() == 0) {
					Property p = ModelConverters.getInstance().readAsProperty(responseType);
					operation.response(responseCode,
							new Response().description(SUCCESSFUL_OPERATION).schema(p).headers(defaultResponseHeaders));
				}
				for (String key : models.keySet()) {
					Property property = new RefProperty().asDefault(key);
					Property responseProperty = ContainerWrapper.wrapContainer(responseContainer, property);
					operation.response(responseCode, new Response().description(SUCCESSFUL_OPERATION)
							.schema(responseProperty).headers(defaultResponseHeaders));
					swagger.model(key, models.get(key));
				}
				models = ModelConverters.getInstance().readAll(responseType);
				for (String key : models.keySet()) {
					swagger.model(key, models.get(key));
				}
			}
		}

		operation.operationId(operationId);

		Annotation annotation;
		if (apiOperation != null && apiOperation.consumes() != null && apiOperation.consumes().isEmpty()) {
			annotation = method.getAnnotation(Consumes.class);
			if (annotation != null) {
				String[] apiConsumes = ((Consumes) annotation).value();
				for (String mediaType : apiConsumes)
					operation.consumes(mediaType);
			}
		}

		if (apiOperation != null && apiOperation.produces() != null && apiOperation.produces().isEmpty()) {
			annotation = method.getAnnotation(Produces.class);
			if (annotation != null) {
				String[] apiProduces = ((Produces) annotation).value();
				for (String mediaType : apiProduces)
					operation.produces(mediaType);
			}
		}

		List<ApiResponse> apiResponses = new ArrayList<ApiResponse>();
		if (responseAnnotation != null) {
			for (ApiResponse apiResponse : responseAnnotation.value()) {
				Map<String, Property> responseHeaders = parseResponseHeaders(apiResponse.responseHeaders());

				Response response = new Response().description(apiResponse.message()).headers(responseHeaders);

				if (apiResponse.code() == 0)
					operation.defaultResponse(response);
				else
					operation.response(apiResponse.code(), response);

				responseType = apiResponse.response();
				if (responseType != null && !isVoid(responseType)) {
					Map<String, Model> models = ModelConverters.getInstance().read(responseType);
					for (String key : models.keySet()) {
						Property property = new RefProperty().asDefault(key);
						Property responseProperty = ContainerWrapper.wrapContainer(apiResponse.responseContainer(),
								property);
						response.schema(responseProperty);
						swagger.model(key, models.get(key));
					}
					models = ModelConverters.getInstance().readAll(responseType);
					for (String key : models.keySet()) {
						swagger.model(key, models.get(key));
					}
				}
			}
		}
		boolean isDeprecated = false;
		annotation = method.getAnnotation(Deprecated.class);
		if (annotation != null)
			isDeprecated = true;

		boolean hidden = false;
		if (apiOperation != null)
			hidden = apiOperation.hidden();

		// process parameters
		Type[] genericParameterTypes = method.getGenericParameterTypes();
		Annotation[][] paramAnnotations = method.getParameterAnnotations();
		for (int i = 0; i < genericParameterTypes.length; i++) {
			Type type = genericParameterTypes[i];
			List<Parameter> parameters = getParameters(type, Arrays.asList(paramAnnotations[i]));

			for (Parameter parameter : parameters) {
				operation.parameter(parameter);
			}
		}
		if (operation.getResponses() == null) {
			operation.defaultResponse(new Response().description(SUCCESSFUL_OPERATION));
		}
		return operation;
	}

	private Map<String, List<Method>> collectApisByRequestMapping(List<Method> methods) {
		Map<String, List<Method>> apiMethodMap = new HashMap<String, List<Method>>();
		for (Method method : methods) {
			if (method.isAnnotationPresent(RequestMapping.class)) {
				RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
				String path = "";
				if (requestMapping.value() != null && requestMapping.value().length != 0) {
					path = generateFullPath(requestMapping.value()[0]);
				} else {
					path = resourcePath;
				}
				if (apiMethodMap.containsKey(path)) {
					apiMethodMap.get(path).add(method);
				} else {
					List<Method> ms = new ArrayList<Method>();
					ms.add(method);
					apiMethodMap.put(path, ms);
				}
			}
		}

		return apiMethodMap;
	}

	private String generateFullPath(String path) {
		if (StringUtils.isNotEmpty(path)) {
			return this.resourcePath + (path.startsWith("/") ? path : '/' + path);
		} else {
			return this.resourcePath;
		}
	}

	private Class<?> getGenericSubtype(Class<?> clazz, Type t) {
		if (!(clazz.getName().equals("void") || t.toString().equals("void"))) {
			try {
				ParameterizedType paramType = (ParameterizedType) t;
				Type[] argTypes = paramType.getActualTypeArguments();
				if (argTypes.length > 0) {
					Class<?> c = (Class<?>) argTypes[0];
					return c;
				}
			} catch (ClassCastException e) {
				// FIXME: find out why this happens to only certain types
			}
		}
		return clazz;
	}

	// Helper method for loadDocuments()
	private Map<String, SpringResource> analyzeController(Class<?> clazz, Map<String, SpringResource> resourceMap,
			String description) throws ClassNotFoundException {
		String controllerCanonicalName = clazz.getCanonicalName();
		String[] controllerRequestMappingValues = null;

		// Determine if we will use class-level requestmapping or dummy string
		if (clazz.getAnnotation(RequestMapping.class) != null
				&& clazz.getAnnotation(RequestMapping.class).value() != null) {
			controllerRequestMappingValues = clazz.getAnnotation(RequestMapping.class).value();
		} else {
			controllerRequestMappingValues = new String[1];
			controllerRequestMappingValues[0] = "";
		}

		// Iterate over all value attributes of the class-level RequestMapping
		// annotation
		for (int i = 0; i < controllerRequestMappingValues.length; i++) {

			// Iterate over all methods inside the controller
			Method[] methods = clazz.getMethods();
			for (Method method : methods) {
				RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);

				// Look for method-level @RequestMapping annotation
				if (methodRequestMapping instanceof RequestMapping) {
					RequestMethod[] requestMappingRequestMethods = methodRequestMapping.method();

					// For each method-level @RequestMapping annotation, iterate
					// over HTTP Verb
					for (RequestMethod requestMappingRequestMethod : requestMappingRequestMethods) {
						String[] methodRequestMappingValues = methodRequestMapping.value();

						// Check for cases where method-level
						// @RequestMapping#value is not set, and use the
						// controllers @RequestMapping
						if (methodRequestMappingValues == null || methodRequestMappingValues.length == 0) {
							// The map key is a concat of the following:
							// 1. The controller package
							// 2. The controller class name
							// 3. The controller-level @RequestMapping#value
							String resourceKey = controllerCanonicalName + controllerRequestMappingValues[i]
									+ requestMappingRequestMethod;
							if ((!(resourceMap.containsKey(resourceKey)))) {
								resourceMap.put(resourceKey, new SpringResource(clazz,
										controllerRequestMappingValues[i], resourceKey, description));
							}
							resourceMap.get(resourceKey).addMethod(method);
						} else {
							// Here we know that method-level
							// @RequestMapping#value is populated, so
							// iterate over all the @RequestMapping#value
							// attributes, and add them to the resource map.
							for (String methodRequestMappingValue : methodRequestMappingValues) {
								String resourceName = methodRequestMappingValue;
								// The map key is a concat of the following:
								// 1. The controller package
								// 2. The controller class name
								// 3. The controller-level @RequestMapping#value
								// 4. The method-level @RequestMapping#value
								// 5. The method-level @RequestMapping#method
								String resourceKey = controllerCanonicalName + controllerRequestMappingValues[i]
										+ resourceName + requestMappingRequestMethod;
								if (!(resourceName.equals(""))) {
									if ((!(resourceMap.containsKey(resourceKey)))) {
										resourceMap.put(resourceKey,
												new SpringResource(clazz, resourceName, resourceKey, description));
									}
									resourceMap.get(resourceKey).addMethod(method);
								}
							}
						}
					}
				}
			}
		}
		clazz.getFields();
		clazz.getDeclaredFields(); // <--In case developer declares a field
									// without an associated getter/setter.
		// this will allow NoClassDefFoundError to be caught before it triggers
		// bamboo failure.

		return resourceMap;
	}

	private Map<String, SpringResource> generateResourceMap(Set<Class<?>> validClasses) throws GenerateException {
		Map<String, SpringResource> resourceMap = new HashMap<String, SpringResource>();
		for (Class<?> c : validClasses) {
			RequestMapping requestMapping = c.getAnnotation(RequestMapping.class);
			String description = "";
			// This try/catch block is to stop a bamboo build from failing due
			// to NoClassDefFoundError
			// This occurs when a class or method loaded by reflections contains
			// a type that has no dependency
			try {
				resourceMap = analyzeController(c, resourceMap, description);
				List<Method> mList = new ArrayList<Method>(Arrays.asList(c.getMethods()));
				if (c.getSuperclass() != null) {
					mList.addAll(Arrays.asList(c.getSuperclass().getMethods()));
				}

			} catch (NoClassDefFoundError e) {
				LOG.error(e.getMessage());
				LOG.info(c.getName());
				// exception occurs when a method type or annotation is not
				// recognized by the plugin
			} catch (ClassNotFoundException e) {
				LOG.error(e.getMessage());
				LOG.info(c.getName());
			}

		}

		return resourceMap;
	}

	private static boolean isVoid(Type type) {
		final Class<?> cls = TypeFactory.defaultInstance().constructType(type).getRawClass();
		return Void.class.isAssignableFrom(cls) || Void.TYPE.isAssignableFrom(cls);
	}

	private static boolean isValidResponse(Type type) {
		final JavaType javaType = TypeFactory.defaultInstance().constructType(type);
		if (isVoid(javaType)) {
			return false;
		}
		final Class<?> cls = javaType.getRawClass();
		return !javax.ws.rs.core.Response.class.isAssignableFrom(cls) && !isResourceClass(cls);
	}

	boolean isPrimitive(Type type) {
		boolean out = false;

		Property property = ModelConverters.getInstance().readAsProperty(type);
		if (property == null)
			out = false;
		else if ("integer".equals(property.getType()))
			out = true;
		else if ("string".equals(property.getType()))
			out = true;
		else if ("number".equals(property.getType()))
			out = true;
		else if ("boolean".equals(property.getType()))
			out = true;
		else if ("array".equals(property.getType()))
			out = true;
		else if ("file".equals(property.getType()))
			out = true;
		return out;
	}

	private static boolean isResourceClass(Class<?> cls) {
		return cls.getAnnotation(Api.class) != null;
	}

	enum ContainerWrapper {
		LIST("list") {
			@Override
			protected Property doWrap(Property property) {
				return new ArrayProperty(property);
			}
		},
		ARRAY("array") {
			@Override
			protected Property doWrap(Property property) {
				return new ArrayProperty(property);
			}
		},
		MAP("map") {
			@Override
			protected Property doWrap(Property property) {
				return new MapProperty(property);
			}
		},
		SET("set") {
			@Override
			protected Property doWrap(Property property) {
				ArrayProperty arrayProperty = new ArrayProperty(property);
				arrayProperty.setUniqueItems(true);
				return arrayProperty;
			}
		};

		private final String container;

		ContainerWrapper(String container) {
			this.container = container;
		}

		public Property wrap(String container, Property property) {
			if (this.container.equalsIgnoreCase(container)) {
				return doWrap(property);
			}
			return null;
		}

		public static Property wrapContainer(String container, Property property, ContainerWrapper... allowed) {
			final Set<ContainerWrapper> tmp = allowed.length > 0 ? EnumSet.copyOf(Arrays.asList(allowed))
					: EnumSet.allOf(ContainerWrapper.class);
			for (ContainerWrapper wrapper : tmp) {
				final Property prop = wrapper.wrap(container, property);
				if (prop != null) {
					return prop;
				}
			}
			return property;
		}

		protected abstract Property doWrap(Property property);
	}
}
