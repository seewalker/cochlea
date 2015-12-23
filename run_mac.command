cochlea_dir=~/Downloads/cochlea
libdir=/usr/local/lib
# is there a way to use an 'LD_LIBRARY_PATH' in a mac '.command'? if not, the following stuff is necessary.
if [ -e $libdir/libscsynth.dylib ]
then
    echo "library in expected place, continuing"
else
    echo "may we place the 'libcsynth' library in /usr/local/lib? (y or n)"
    read confirm
    if [ $confirm = "y" ]
        then
            echo cp $cochlea_dir/target/base+system+user+dev/native/macosx/x86_64/libscsynth.dylib /usr/local/lib
            cp $cochlea_dir/target/base+system+user+dev/native/macosx/x86_64/libscsynth.dylib /usr/local/lib
        else
            echo "this app won't run, then."
    fi
fi
java -jar $cochlea_dir/cochlea.jar
