package net.fabricmc.loom;

import net.fabricmc.loom.providers.DependencyProvider;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency-injection management for DependencyProviders. Sounds a bit scarier than it is?
 * <p>
 * The "dependency provider" stuff is a form of task graph,
 * so it helps to have something that can take care of all the annoying interrelations.
 * 
 * @see DependencyProvider
 */
public class ProviderGraph {
	public ProviderGraph(Project project, LoomGradleExtension extension) {
		this.project = project;
		this.extension = extension;
	}
	
	private final Project project;
	private final LoomGradleExtension extension;
	
	private final Map<Class<? extends DependencyProvider>, DependencyProvider> constructedProviders = new HashMap<>();
	
	//No computeIfAbsent because of ConcurrentModificationException, constructProviderOfType can set entries in the map too
	@SuppressWarnings("unchecked")
	public <T extends DependencyProvider> T getProviderOfType(Class<T> type) {
		if(constructedProviders.containsKey(type)) {
			return (T) constructedProviders.get(type);
		} else {
			DependencyProvider newlyConstructed = constructProviderOfType(type);
			constructedProviders.put(type, newlyConstructed);
			return (T) newlyConstructed;
		}
	}
	
	private DependencyProvider constructProviderOfType(Class<? extends DependencyProvider> type) {
		project.getLogger().info("|-> Constructing " + type.getSimpleName() + "...");
		
		//"Note that while this method returns an array of Constructor<T> objects [...],
		//the return type of this method is Constructor<?>. [...] This less informative
		//return type is necessary since after being returned from this method, the
		//array could be modified to hold Constructor objects for different classes,
		//which would violate the type guarantees of Constructor<T>."
		//Thanks java!!!!!! Very nice
		for(Constructor<?> consFunny : type.getConstructors()) {
			@SuppressWarnings("unchecked")
			Constructor<? extends DependencyProvider> cons = (Constructor<? extends DependencyProvider>) consFunny;
			
			if(!cons.isAnnotationPresent(Inject.class)) continue;
			
			//Try to fill out the constructor arguments
			List<Object> realizedParameters = new ArrayList<>();
			for(Class<?> parameterType : cons.getParameterTypes()) {
				if(parameterType == Project.class) {
					realizedParameters.add(project);
				} else if(parameterType == LoomGradleExtension.class) {
					realizedParameters.add(extension);
				} else if(parameterType == ProviderGraph.class) {
					//Hehe
					realizedParameters.add(this);
				} else if(DependencyProvider.class.isAssignableFrom(parameterType)) {
					//Recurse!
					//If this blows the stack, well, there's a circular dependency somewhere, try not doing that
					realizedParameters.add(getProviderOfType(parameterType.asSubclass(DependencyProvider.class)));
				} else {
					throw new IllegalStateException("Can't construct provider of type " + type + " - don't know how to construct argument type " + parameterType);
				}
			}
			
			//Now that we have suitable consturctor arguments, we can call the constructor
			try {
				return cons.newInstance(realizedParameters.toArray());
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException("Can't construct provider of type " + type, e);
			}
		}
		
		throw new IllegalStateException("Can't construct provider of type " + type + " - couldn't find suitable constructor. Is one annotated with @Inject?");
	}
}
