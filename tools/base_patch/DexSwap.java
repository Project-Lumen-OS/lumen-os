// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

/**
 * DexSwap BASE.dex FIX.dex OUT.dex OUTER API: OUT = BASE with the class OUTER and its nested classes (OUTER$...)
 * replaced by the classes of FIX. FIX may hold nothing else; every other class of BASE is written unchanged.
 */
public final class DexSwap {
    public static void main(String[] a) throws Exception {
        if (a.length != 5) throw new IllegalArgumentException("DexSwap BASE.dex FIX.dex OUT.dex Lpkg/Outer; API");
        Opcodes op = Opcodes.forApi(Integer.parseInt(a[4]));
        DexFile base = DexFileFactory.loadDexFile(new File(a[0]), op);
        DexFile fix = DexFileFactory.loadDexFile(new File(a[1]), op);
        String outer = a[3];
        String nested = outer.substring(0, outer.length() - 1) + "$";
        Set<String> gone = new TreeSet<>(), added = new TreeSet<>();
        DexPool pool = new DexPool(op);
        int kept = 0;
        for (ClassDef c : base.getClasses()) {
            if (c.getType().equals(outer) || c.getType().startsWith(nested)) {
                gone.add(c.getType());
            } else {
                pool.internClass(c);
                kept++;
            }
        }
        for (ClassDef c : fix.getClasses()) {
            if (!c.getType().equals(outer) && !c.getType().startsWith(nested)) {
                throw new IllegalStateException("the fix dex holds " + c.getType() + ", outside " + outer);
            }
            added.add(c.getType());
            pool.internClass(c);
        }
        if (!gone.contains(outer) || !added.contains(outer)) {
            throw new IllegalStateException(outer + " missing in the base or the fix dex");
        }
        pool.writeTo(new FileDataStore(new File(a[2])));
        System.out.println("kept " + kept + " classes, replaced " + gone + " with " + added);
    }
}
