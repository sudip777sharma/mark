package dev.mark.agent.service.ui;

import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class DeterministicIdentityGenerator {

    public static UiTreeModel generateIdentities(UiTreeModel tree) {
        if (tree.root() == null) {
            return tree;
        }

        UiElementModel newRoot = traverseAndGenerate(tree.root(), tree.applicationName(), tree.windowTitle());
        return new UiTreeModel(tree.applicationName(), tree.windowTitle(), newRoot);
    }

    private static UiElementModel traverseAndGenerate(UiElementModel node, String parentPath, String windowTitle) {
        String currentName = node.name() != null && !node.name().isEmpty() ? node.name() : node.role();
        String currentPath = parentPath == null || parentPath.isEmpty() ? currentName : parentPath + " > " + currentName;

        // Hash components: windowTitle, role, name, className, path
        String rawToHash = String.format("%s|%s|%s|%s|%s",
                windowTitle != null ? windowTitle : "",
                node.role() != null ? node.role() : "",
                node.name() != null ? node.name() : "",
                node.className() != null ? node.className() : "",
                currentPath
        );

        String id = "ui-" + hash(rawToHash);

        List<UiElementModel> newChildren = new ArrayList<>();
        for (UiElementModel child : node.children()) {
            newChildren.add(traverseAndGenerate(child, currentPath, windowTitle));
        }

        return new UiElementModel(
                id,
                node.role(),
                node.name(),
                node.className(),
                currentPath,
                node.bounds(),
                node.actions(),
                node.isActionable(),
                newChildren
        );
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 8); // 8 char short hash
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
