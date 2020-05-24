package de.espend.idea.php.annotation.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.documentation.phpdoc.parser.PhpDocElementTypes;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import de.espend.idea.php.annotation.inspection.visitor.PhpDocTagWithUsePsiElementVisitor;
import de.espend.idea.php.annotation.util.PhpElementsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

/**
 * Check if there is a PhpClass available for a doc block based on the use statement
 *
 * - absolute is directly resolve: "@\Foobar"
 * - other needs a valid use statement "use Foobar;" => "@Foobar" do active a check
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class AnnotationDocBlockTagClassNotFoundInspection extends LocalInspectionTool {
    public static final String MESSAGE = "Class not found";

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PhpDocTagWithUsePsiElementVisitor(holder, this::visitAnnotationDocTag);
    }

    private void visitAnnotationDocTag(@NotNull PhpDocTag phpDocTag, @NotNull ProblemsHolder holder, @NotNull Function<Void, Map<String, String>> lazyUseImporterCollector) {
        // Target for our inspection is DocTag name: @Foobar() => Foobar
        // This prevent highlighting the complete DocTag
        PsiElement firstChild = phpDocTag.getFirstChild();
        if (firstChild == null || firstChild.getNode().getElementType() != PhpDocElementTypes.DOC_TAG_NAME) {
            return;
        }

        String name = phpDocTag.getName();
        String tagName = StringUtils.stripStart(name, "@");

        // ignore "@\Foo" absolute FQN ones


        String clazz = null;
        if (tagName.startsWith("\\")) {
            // absolute class use it directly
            clazz = tagName;
        } else {
            String[] split = tagName.split("\\\\");
            Map<String, String> useImportMap = lazyUseImporterCollector.apply(null);
            if (useImportMap.containsKey(split[0])) {
                clazz = useImportMap.get(split[0]);

                // based on the use statement, which can be an alias, attach the doc block class name
                // "@Foobar\TTest" "Foo\Foobar"
                if (split.length > 1) {
                    clazz += "\\" + StringUtils.join(Arrays.copyOfRange(split, 1, split.length), "\\");
                }
            }
        }

        if (clazz == null) {
            return;
        }

        PhpClass classInterface = PhpElementsUtil.getClassInterface(phpDocTag.getProject(), clazz);
        if (classInterface == null) {
            holder.registerProblem(
                firstChild,
                MESSAGE,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            );
        }
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }
}