package com.peregrine.versions;

import com.google.common.collect.Iterators;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.wrappers.ResourceResolverWrapper;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.Version;
import javax.jcr.version.VersionManager;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.peregrine.commons.Strings.SLASH;
import static com.peregrine.commons.util.PerConstants.JCR_CONTENT;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.*;

public final class VersioningResourceResolverImpl extends ResourceResolverWrapper implements VersioningResourceResolver {

    private static final String _JCR_CONTENT_ = SLASH + JCR_CONTENT + SLASH;

    private final List<String> exemptedPrimaryTypes = Arrays.asList(
            "nt:folder",
            "sling:Folder",
            "sling:OrderedFolder",
            "per:Site"
    );
    private final List<String> forcedPaths = Arrays.asList(
            "/content/"
    );
    private final List<String> exemptedPaths = Arrays.asList(
            "/content/admin",
            "/content/docs",
            "/content/apidocs"
    );

    private final ResourceResolver resolver;
    private final VersionManager versionManager;
    private final String versionLabel;

    public VersioningResourceResolverImpl(final ResourceResolver resolver, final String versionLabel)
            throws PersistenceException {
        super(resolver);
        this.resolver = resolver;
        try {
            versionManager = resolver.adaptTo(Session.class)
                    .getWorkspace()
                    .getVersionManager();
        } catch (final RepositoryException e) {
            throw new PersistenceException("VersionManager not available.", e);
        }

        this.versionLabel = versionLabel;
    }

    @Override
    public Iterator<Resource> listChildren(final Resource parent) {
        final Resource wrappedResource = Optional.of(parent)
                .map(VersionedResource::unwrap)
                .map(r -> (Resource) r)
                .orElseGet(() -> wrap(parent));
        if (!(wrappedResource instanceof VersionedResource)) {
            return Iterators.transform(
                    resolver.listChildren(wrappedResource),
                    child -> new VersionedResource(this, child)
            );
        }

        final VersionedResource versionedResource = (VersionedResource) wrappedResource;
        final Resource parentVersion = versionedResource.getVersion();
        if (isNull(parentVersion)) {
            final Resource resource = versionedResource.getResource();
            final Iterable<Resource> iterable = () -> resolver.listChildren(resource);
            return StreamSupport.stream(iterable.spliterator(), false)
                    .map(this::wrap)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
                    .iterator();
        }

        final String parentPath = parent.getPath();
        return Iterators.transform(
                resolver.listChildren(parentVersion),
                child -> new VersionedResource(this, parentPath, child)
        );
    }

    @Override
    public Resource getResource(final String path) {
        return Optional.ofNullable(path)
                .map(resolver::getResource)
                .map(this::wrap)
                .orElse(null);
    }

    public Resource wrap(final Resource resource) {
        if (isNull(resource) || resource instanceof VersionedResource) {
            return resource;
        }

        final ResourceResolver resourceResolver = resource.getResourceResolver();
        final String path = resource.getPath();
        if (ResourceUtil.isNonExistingResource(resource)) {
            return resourceResolver == this ? resource
                    : new NonExistingResource(this, path);
        }

        if (ResourceUtil.isSyntheticResource(resource)) {
            return resourceResolver == this ? resource
                    : new SyntheticResource(this, path, resource.getResourceType());
        }

        if (ResourceUtil.isStarResource(resource)) {
            return resourceResolver == this ? resource
                    : new VersionedResource(this, resource);
        }

        if (!forceVersion(resource)) {
            return new VersionedResource(this, resource);
        }

        final Resource content = resource.getChild(JCR_CONTENT);
        if (nonNull(content)) {
            if (isVersioned(content)) {
                return new VersionedResource(this, resource);
            }

            return null;
        }

        if (JCR_CONTENT.equals(resource.getName())) {
            final Resource versionedNode = getVersionedNode(resource);
            if (nonNull(versionedNode)) {
                return new VersionedResource(this, resource, versionedNode);
            }
        } else if (path.contains(_JCR_CONTENT_)) {
            final String ancestorPath = substringBeforeLast(path, _JCR_CONTENT_) + SLASH + JCR_CONTENT;
            final Resource versionedNode = Optional.ofNullable(getVersionedNode(ancestorPath))
                    .map(r -> r.getChild(substringAfterLast(path, _JCR_CONTENT_)))
                    .orElse(null);
            if (nonNull(versionedNode)) {
                return new VersionedResource(this, resource, versionedNode);
            }
        }

        return null;
    }

    private boolean forceVersion(final Resource resource) {
        if (exemptedPrimaryTypes.stream()
                .map(resource::isResourceType)
                .anyMatch(x -> x)
        ) {
            return false;
        }

        final String path = resource.getPath();
        if (exemptedPaths.stream().anyMatch(path::equals)) {
            return false;
        }

        if (exemptedPaths.stream()
                .map(p -> p + SLASH)
                .anyMatch(path::startsWith)
        ) {
            return false;
        }

        return forcedPaths.stream().anyMatch(path::startsWith);
    }

    private Resource getVersionedNode(final Resource resource) {
        return getVersionedNode(resource.getPath());
    }

    private boolean isVersioned(final Resource resource) {
        return nonNull(getVersionedNode(resource));
    }

    private Resource getVersionedNode(final String path) {
        try {
            final Version version = versionManager
                    .getVersionHistory(path)
                    .getVersionByLabel(versionLabel);
            if (nonNull(version)) {
                final Node node = version.getFrozenNode();
                final String versionPath = node.getPath();
                return resolver.getResource(versionPath);
            }

        } catch (final RepositoryException e) {
            return null;
        }

        return null;
    }

    @Override
    public Resource getResource(final Resource base, final String path) {
        String fullPath = path;
        if (nonNull(base) && !startsWith(fullPath, SLASH)) {
            fullPath = base.getPath() + SLASH + fullPath;
        }

        return getResource(fullPath);
    }

    @Override
    public <AdapterType> AdapterType adaptTo(final Class<AdapterType> type) {
        return Optional.of(getClass())
                .filter(type::isAssignableFrom)
                .map(type::cast)
                .orElseGet(() -> resolver.adaptTo(type));
    }

    @Override
    public Iterable<Resource> getChildren(final Resource parent) {
        return () -> listChildren(parent);
    }

    @Override
    public boolean hasChildren(final Resource resource) {
        return listChildren(resource).hasNext();
    }

    @Override
    public Resource getParent(final Resource child) {
        return Optional.of(child)
                .map(Resource::getPath)
                .map(ResourceUtil::getParent)
                .map(this::getResource)
                .orElse(null);
    }

    @Override
    public Resource resolve(final String absPath) {
        return resolve(null, absPath);
    }

    @Override
    public Resource resolve(final HttpServletRequest request) {
        return resolve(request, request.getPathInfo());
    }

    @Override
    public Resource resolve(final HttpServletRequest request, final String absPath) {
        final Resource resource = resolver.resolve(request, absPath);
        final Resource wrap = wrap(resource);
        return nonNull(wrap) ? wrap : new NonExistingResource(this, resource.getPath());
    }

    @Override
    public Iterator<Resource> findResources(final String query, final String language) {
        return Iterators.transform(resolver.findResources(query, language), this::wrap);
    }

    @Override
    public Iterator<Map<String, Object>> queryResources(final String query, final String language) {
        return Iterators.transform(findResources(query, language), Resource::getValueMap);
    }

}
