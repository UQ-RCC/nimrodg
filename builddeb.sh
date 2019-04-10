#!/bin/bash
set -e

GRADLE="gradle -q --console=plain"
VERSION=$(${GRADLE} version | awk '{ print $2; }')

${GRADLE} assembleDist

TARBALL=$PWD/nimrod/build/distributions/nimrod-${VERSION}.tar
if [ ! -f ${TARBALL} ]; then
    echo "Tarball ${TARBALL} doesn't exist."
    exit 1
fi

pushd debian

chmod 755 DEBIAN/postinst DEBIAN/postrm

mkdir -p usr/share
chmod 755 usr usr/share

pushd usr/share
rm -rf nimrod nimrod-*
tar -xf ${TARBALL}
mv nimrod-${VERSION} nimrod

find . -type f -name "*.jar" -print0 | xargs -0 chmod 644
find . -type d -print0 | xargs -0 chmod 755
rm -f ./nimrod/bin/nimrod.bat

popd



cat <<EOF > DEBIAN/control
Source: nimrodg
Section: devel
Priority: optional
Maintainer: $(git config --get user.name) <$(git config --get user.email)>
Standards-Version: 3.9.5
Build-Depends: openjdk-11-jdk-headless, gradle (>= 4.7)
Homepage: https://rcc.uq.edu.au/nimrod
Package: nimrodg
Version: ${VERSION}-0ubuntu1
Architecture: all
Depends: openjdk-11-jre-headless
Recommends: postgresql-10, rabbitmq-server
Description: Nimrod is a specialised parametric modelling system. 
 It uses a simple declarative parametric modelling language to express
 a parametric experiment. 
 .
 It provides the machinery to automate the task of formulating, running,
 monitoring, collating, presenting and visualising the results from
 multiple individual experiments. 
 .
 Nimrod incorporates distributed scheduling so that the appropriate
 number and kind of resources to complete the job, e.g., HPC and
 virtual machines, can be selected and used. 
 .
 Nimrod helps researchers run computations remotely on the cloud. It can
 turn your laptop into a supercomputer. With Nirmod you can run many
 jobs — millions if need be. 
EOF

popd

fakeroot dpkg --build debian
mv debian.deb nimrodg-${VERSION}.deb
lintian nimrodg-${VERSION}.deb
