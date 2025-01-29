package io.nextflow.gradle.github

import com.google.gson.Gson
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

/**
 * Simple Github HTTP client
 *
 * https://stackoverflow.com/a/63461333/395921
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@PackageScope
class GithubClient {
    String authToken
    String userName
    String branch
    String repo
    String owner
    String email

    private Gson gson = new Gson()

    private String getEncodedAuthToken() {
        if (!userName)
            throw new IllegalArgumentException("Missing Github userName")
        if (!authToken)
            throw new IllegalArgumentException("Missing Github authToken")
        return "$userName:$authToken".bytes.encodeBase64().toString()
    }

    private HttpURLConnection getHttpConnection(String url) {
        new URL(url).openConnection() as HttpURLConnection
    }

    private sendHttpMessage(String endpoint, String payload, String method = 'POST') {
        if (!endpoint)
            throw new IllegalArgumentException("Missing Github target endpoint")

        def con = getHttpConnection(endpoint)
        // Make header settings
        con.setRequestMethod(method)
        con.setRequestProperty("Content-Type", "application/json")
        con.setRequestProperty("Authorization", "Basic ${getEncodedAuthToken()}")

        con.setDoOutput(true)

        // Send POST request
        if (payload) {
            DataOutputStream output = new DataOutputStream(con.getOutputStream())
            output.writeBytes(payload)
            output.flush()
            output.close()
        }

        int code
        try {
            code = con.responseCode
            final text = con.getInputStream().text
            log.trace "resp code=$code, text=$text"
            return gson.fromJson(text, Map)
        }
        catch (IOException e) {
            final text = con.getErrorStream().text
            throw new IllegalStateException("Unexpected response code=$code\n- response=$text\n- request=$endpoint\n- payload=$payload")
        }
    }

    /**
     * 1. Get the last commit SHA of a specific branch
     *
     * @return the SHA id of the last commit
     */
    private String lastCommitId() {
        def resp = sendHttpMessage("https://api.github.com/repos/$owner/$repo/branches/$branch", null, 'GET')
        return resp.commit.sha
    }

    /**
     *  2. Create the blobs with the file content
     *
     * @param file content
     * @return the SHA id of the uploaded content
     */
    private String uploadBlob(String file) {
        def content = "{\"encoding\": \"base64\", \"content\": \"${file.bytes.encodeBase64().toString()}\"}"
        def resp = sendHttpMessage("https://api.github.com/repos/$owner/$repo/git/blobs", content, 'POST')
        return resp.sha
    }

    /**
     * 3. Create a tree that defines the file structure
     *
     * @param fileName the name of the file changed
     * @param blobId the id of the changed content
     * @param lastCommitId the last commit id
     * @return the SHA id of the tree structure
     */
    private String createTree(String fileName, String blobId, String lastCommitId) {
        def content = "{ \"base_tree\": \"$lastCommitId\", \"tree\": [{\"path\": \"$fileName\",\"mode\": \"100644\",\"type\": \"blob\",\"sha\": \"$blobId\"}]}"
        def resp = sendHttpMessage("https://api.github.com/repos/$owner/$repo/git/trees", content, 'POST')
        return resp.sha
    }

    /**
     * 4. Create the commit
     *
     * @param treeId the change tree SHA id
     * @param lastCommitId the last commit SHA id
     * @param message the commit message
     * @param author the commit author name
     * @param email the commit author email address
     * @return the SHA id of the commit
     */
    private String createCommit(String treeId, String lastCommitId, String message, String email) {
        def content = "{\"message\": \"$message\", \"author\": {\"name\": \"$userName\", \"email\": \"$email\"}, \"parents\": [\"$lastCommitId\"], \"tree\": \"$treeId\" }"
        def resp = sendHttpMessage("https://api.github.com/repos/$owner/$repo/git/commits", content, 'POST')
        return resp.sha
    }

    /**
     * 5. Update the reference of your branch to point to the new commit SHA
     *
     * @param commitId
     * @return the response message
     */
    private def updateRef(String commitId) {
        def content = "{\"ref\": \"refs/heads/$branch\", \"sha\": \"$commitId\"}"
        def resp = sendHttpMessage("https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch", content, 'POST')
        return resp
    }

    void pushChange(String fileName, String content, String message) {
        if (content == null)
            throw new IllegalArgumentException("Missing content argument")
        if (!fileName)
            throw new IllegalArgumentException("Missing fileName argument")
        if (!email)
            throw new IllegalArgumentException("Missing email argument")

        final lastCommit = lastCommitId()
        final blobId = uploadBlob(content)
        final treeId = createTree(fileName, blobId, lastCommit)
        final commitId = createCommit(treeId, lastCommit, message, email)
        updateRef(commitId)
    }

    String getContent(String path) {
        def resp = sendHttpMessage("https://api.github.com/repos/$owner/$repo/contents/$path", null, 'GET')
        def bytes = resp.content?.toString()?.decodeBase64()
        return bytes != null ? new String(bytes) : null
    }

    Map getRelease(String version) {
        def action = "https://api.github.com/repos/$owner/$repo/releases/tags/$version"
        try {
            def resp = sendHttpMessage(action, null, 'GET')
            return resp
        }
        catch (Exception e) {
            return null
        }
    }

    Map createRelease(String version, boolean prerelease = false) {
        final action = "https://api.github.com/repos/${owner}/${repo}/releases"
        final payload = "{\"tag_name\":\"$version\", \"name\": \"Version $version\", \"draft\":false, \"prerelease\":$prerelease}"
        Map resp = sendHttpMessage(action, payload, 'POST')
        return resp
    }

    List listReleases() {
        final action = "https://api.github.com/repos/${owner}/${repo}/releases"
        return (List) sendHttpMessage(action, null, 'GET')
    }

    /**
     * https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#get-a-release-asset
     */
    InputStream getAsset(String assetId) {
        final action = "https://api.github.com/repos/$owner/$repo/releases/assets/$assetId"
        final con = getHttpConnection(action)

        // Make header settings
        con.setRequestMethod('GET')
        con.setRequestProperty("Content-Type", "application/json")
        con.setRequestProperty("Authorization", "Basic ${getEncodedAuthToken()}")
        con.setRequestProperty("Accept", "application/octet-stream")

        con.setDoOutput(true)

        return con.getInputStream()
    }

    InputStream getReleaseAsset(Map release, String name) {
        if (!release) return null
        def asset = (Map) release.assets.find { it.name == name }
        if (!asset) return null

        return getAsset((asset.id as Long).toString())
    }

    /**
     * https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#upload-a-release-asset
     */
    def uploadReleaseAsset(Map release, File file, mimeType) {
        if (!release) return
        final releaseId = (release.id as Long).toString()

        def action = "https://uploads.github.com/repos/${owner}/${repo}/releases/${releaseId}/assets?name=${file.name}"

        def con = getHttpConnection(action)
        // Make header settings
        con.setRequestMethod('POST')
        con.setRequestProperty("Content-Type", mimeType)
        con.setRequestProperty("Content-Length", file.size().toString())
        con.setRequestProperty("Authorization", "Basic ${getEncodedAuthToken()}")

        con.setDoOutput(true)

        DataOutputStream output = new DataOutputStream(con.getOutputStream())
        output.write(file.bytes)
        output.flush()
        output.close()

        def resp = con.responseCode >= 400
            ? con.getErrorStream().text
            : con.getInputStream().text

        return resp
    }
}
