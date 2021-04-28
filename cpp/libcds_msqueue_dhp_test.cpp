#include <gtest/gtest.h>
#include <gmock/gmock.h>
#include <cds/gc/dhp.h>
#include <cds/init.h>
#include <cds/container/msqueue.h>
#include <queue>
#include "lincheck.h"

using namespace Lincheck;
using ::testing::HasSubstr;

using queue_type = cds::container::MSQueue<cds::gc::DHP, int>;

std::vector<int> queue_to_vector(queue_type &queue) {
    std::vector<int> ans;
    while (queue.dequeue_with([&ans](int &src) { ans.push_back(src); })) {}
    for(auto elem : ans) {
        queue.enqueue(elem);
    }
    return ans;
}

template<>
struct Lincheck::hash<std::vector<int>> {
    std::size_t operator()(const std::vector<int> &v) const noexcept {
        std::string s;
        for(auto elem : v) {
            s += std::to_string(elem) + ",";
        }
        return std::hash<std::string>()(s);
    }
};

template<>
struct Lincheck::to_string<std::pair<bool, int>> {
    std::string operator()(const std::pair<bool, int> &ret) const noexcept {
        return ret.first ? "{true, " + std::to_string(ret.second) + "}" : "{false, " + std::to_string(ret.second) + "}";
    }
};

template<>
struct Lincheck::hash<queue_type> {
    std::size_t operator()(queue_type &q) const noexcept {
        return Lincheck::hash<std::vector<int>>()(queue_to_vector(q));
    }
};

class LibcdsQueue {
public:
    queue_type queue;

    bool push(queue_type::value_type val) {
        return queue.enqueue(val);
    }

    std::pair<bool, queue_type::value_type> pop() {
        queue_type::value_type ans = 0;
        bool success = queue.dequeue(ans);
        return {success, ans};
    }
};

template<>
struct Lincheck::hash<std::pair<bool, queue_type::value_type>> {
    std::size_t operator()(std::pair<bool, queue_type::value_type> &p) const noexcept {
        return p.first ? Lincheck::hash<queue_type::value_type>()(p.second) : -1 * Lincheck::hash<queue_type::value_type>()(p.second);
    }
};

template<>
struct Lincheck::hash<LibcdsQueue> {
    std::size_t operator()(LibcdsQueue &q) const noexcept {
        return Lincheck::hash<std::vector<int>>()(queue_to_vector(q.queue));
    }
};

bool operator==(LibcdsQueue &a, LibcdsQueue &b) {
    return queue_to_vector(a.queue) == queue_to_vector(b.queue);
}

void myAttach() {
    //std::string val = "attached " + std::to_string(cds::OS::get_current_thread_id()) + "\n";
    //std::cout << val;
    cds::threading::Manager::attachThread();
}

void myDetach() {
    //std::string val = "detached " + std::to_string(cds::OS::get_current_thread_id()) + "\n";
    //std::cout << val;
    cds::threading::Manager::detachThread();
}

TEST(LibcdsMSQueueTest, QueueTest) {
    cds::Initialize();

    {
        cds::gc::DHP dhpGC(
                160 //dhp_init_guard_count
        );

        myAttach();

        LincheckConfiguration<LibcdsQueue, LibcdsQueue> conf;
        conf.iterations(10);
        conf.invocationsPerIteration(500);
        conf.minimizeFailedScenario(false);

        conf.initThreadFunction<myAttach>();
        conf.finishThreadFunction<myDetach>();

        conf.operation<bool, int, &LibcdsQueue::push, &LibcdsQueue::push>("push");
        conf.operation<std::pair<bool, int>, &LibcdsQueue::pop, &LibcdsQueue::pop>("pop");
        ASSERT_EQ(conf.runTest(false), "");

        myDetach();
    }

    cds::Terminate();
}