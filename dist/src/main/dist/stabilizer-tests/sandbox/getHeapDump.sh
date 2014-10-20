#!/bin/sh

max=1
everySeconds=1

ips=$(cat agents.txt | cut -d',' -f1)

#Set the field separator to new line
IFS=$'\n'


for (( i=1; i<=$max; i++ ))
do
    for box in $ips
    do
        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no stabilizer@${box} "rm -f heap.bin; jps | grep Member.* | cut -d ' ' -f1 | xargs -L 1  jmap -heap:format=b $1"

        scp stabilizer@${box}:heap.bin ${box}MemberHeap.bin
    done


    sleep ${everySeconds}
done

echo "The End"
